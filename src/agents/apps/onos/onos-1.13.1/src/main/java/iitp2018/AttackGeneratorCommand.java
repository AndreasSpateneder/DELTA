package iitp2018;

import com.google.common.collect.ImmutableSet;
import iitp2018.AttackGenerator;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.core.CoreService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.link.LinkAdminService;
import org.onosproject.net.link.LinkService;
import org.onosproject.openflow.controller.OpenFlowController;

import java.util.ArrayList;

/** Attack Generator CLI for IITP2018
 * usage: onos> delta:attack [attack_num]
 */
@Command(scope = "delta", name = "attack",
        description = "Execute specific attack cases")
public class AttackGeneratorCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "case_num",
            description = "Attack Case Number",
            required = true, multiValued = false)
    String cmd = null;

    private ArrayList<Thread> threads = new ArrayList<>();

    @Override
    protected void execute() {

        OpenFlowController ctrl = get(OpenFlowController.class);
        DeviceService ds = get(DeviceService.class);
        FlowRuleService fs = get(FlowRuleService.class);
        CoreService cs = get(CoreService.class);
        LinkService ls = get(LinkService.class);
        LinkAdminService las = get (LinkAdminService.class);

        AttackGenerator attackGenerator = new AttackGenerator(ctrl, ds, fs, cs, ls, las);

        switch(cmd) {
            case "1":
                attackGenerator.loopInjector();
                break;
            case "2":
                attackGenerator.modifyOutputActions();
                break;
            case "3":
                attackGenerator.removeLinkInformation(threads);
                break;
            case "4":
                attackGenerator.exitController();
                break;
            case "5":
                attackGenerator.exhaustMemResources(threads);
                break;
            case "6":
                attackGenerator.exhaustCpuResources(threads);
                break;
            case "kill":
                System.out.println("Kill threads!");
                for (Thread t: threads) {
                    t.interrupt();
                }
                break;

        }
    }
}
