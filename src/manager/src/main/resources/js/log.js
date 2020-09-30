/**
 * Created by jinwookim on 2018. 7. 21..
 */

$(document).ready(function () {
    var testcase_table = $('#testcase-table').DataTable({
        dom: 'frBtlip',
        buttons: [
            'selectAll',
            'selectNone',
            {
                text: "Queue selected",
                action: function (e, dt, node, config) {

                    var queueArray = new Array();
                    dt.rows({selected: true}).every(function (rowIdx, tableLoop, rowLoop) {
                        queueArray.push(this.data().casenum);
                    });

                    $.ajax({
                        url: "/json/testqueue/post",
                        type: "POST",
                        data: queueArray.toString(),
                        dataType: "text",
                        contentType: "text/plain",
                        async: false,
                        success: function (data) {
                            alert(data);
                        },
                        error: function (xhr, ajaxOptions, thrownError) {
                            alert(xhr.status);
                            alert(thrownError);
                        }

                    });
                }
            }
        ],
        select: {
            style: 'multi'
        },
        lengthMenu: [10, 20, 50, 100],
        searching: true,
        'columns': [
            {'data': 'category'},
            {'data': 'casenum'},
            {'data': 'name'},
        ],
        'ajax': {
            'url': '/json/testcases',
            'dataSrc': ""
        },
        "createdRow": function (row, data) {
            $(row).attr('title', data['desc']);
            $(row).tooltip({
                "delay": 0,
                "track": true,
                "fade": 250
            });
        },
    });

    var queue_table = $('#queue-table').DataTable({
        dom: 'frtlip',
        select: {
            style: 'multi'
        },
        lengthMenu: [10, 20, 50, 100],
        searching: true,
        'columns': [
            {'data': 'index'},
            {'data': 'time'},
            {'data': 'category'},
            {'data': 'casenum'},
            {'data': 'name'},
            {'data': 'status'},
            {'data': 'result'}
        ],
        'ajax': {
            'url': '/json/testqueue/get',
            'dataSrc': ""
        },
        "fnRowCallback": function (nRow, aData) {
            if (aData['status'] == "RUNNING") {
                $('td', nRow).css('background-color', '#ffff99');
            }
            else if (aData['status'] == "COMPLETE") {
                if (aData['result'] == "FAIL") {
                    $('td', nRow).css('background-color', '#ff9999');
                }
                else if (aData['result'] == "PASS") {
                    $('td', nRow).css('background-color', '#ccff99');
                }
                else {
                    $('td', nRow).css('background-color', '#ffffff');
                }
            }
        },
        "createdRow": function (row, data) {
            $(row).attr('title', data['desc']);
            $(row).tooltip({
                "delay": 0,
                "track": true,
                "fade": 250
            });
        },
    });

    // setInterval(function () {
    //     queue_table.ajax.reload();
    // }, 3000);

    var managerTimer = null;
    var startManagerLog = function () {
        managerTimer = window.setInterval(function () {

            $.ajax({
                url: '/text/getlog',
                dataType: 'text',
                contentType: "text/plain",
                async: false,
                success: function (data) {
					handleDeltaData(data);
                }
            });
        }, 1000);
        return managerTimer;
    }

    var channelTimer = null;
    var startChannelLog = function () {
        channelTimer = setInterval(function () {

            $.ajax({
                url: '/text/getchannel',
                dataType: 'text',
                contentType: "text/plain",
                async: false,
                success: function (data) {
					handleChannelData(data);
                }
            });
        }, 1000);
    }

//        $('#channellog')
//            .hover(function () {
//                clearInterval(channelTimer)
//            }, function () {
//                startChannelLog();
//            });

    var appTimer = null;
    var startApplog = function () {
        appTimer = window.setInterval(function () {

            $.ajax({
                url: '/text/getapp',
                dataType: 'text',
                contentType: "text/plain",
                async: false,
                success: function (data) {
					handleAppData(data);
                }
            });
        }, 1000);
        return appTimer;
    }

/*    $('#applog').hover(function () {
        window.clearInterval(appTimer);
    }, function () {
        appTimer = startApplog();
    });
*/
    var hostTimer = null;
    var startHostlog = function () {
        hostTimer = window.setInterval(function () {

            $.ajax({
                url: '/text/gethost',
                dataType: 'text',
                contentType: "text/plain",
                async: false,
                success: function (data) {
					handleHostData(data);
                }
            });
        }, 1000);
    }

//        $('#hostlog').hover(function () {
//            clearInterval(hostTimer)
//        }, function () {
//            startHostlog();
//        });

    startManagerLog();
    startApplog();
    startChannelLog();
    startHostlog();

});
