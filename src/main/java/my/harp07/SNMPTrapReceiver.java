package my.harp07;

import java.io.IOException;
import java.net.UnknownHostException;
import org.apache.commons.lang3.StringUtils;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

public class SNMPTrapReceiver implements CommandResponder {

    private MultiThreadedMessageDispatcher dispatcher;
    private Snmp snmp = null;
    private Address listenAddress;
    private ThreadPool threadPool;

    public void run() {
        try {
            init();
            snmp.addCommandResponder(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void init() throws UnknownHostException, IOException {
        threadPool = ThreadPool.create("Trap", 10);
        dispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
        listenAddress = GenericAddress.parse("udp:0.0.0.0/162");
        TransportMapping<?> transport;
        if (listenAddress instanceof UdpAddress) {
            transport = new DefaultUdpTransportMapping((UdpAddress) listenAddress);
        } else {
            transport = new DefaultTcpTransportMapping((TcpAddress) listenAddress);
        }
        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(
                MPv3.createLocalEngineID()), 0);
        usm.setEngineDiscoveryEnabled(true);
        snmp = new Snmp(dispatcher, transport);
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3(usm));
        SecurityModels.getInstance().addSecurityModel(usm);
        snmp.getUSM().addUser(
                new OctetString("MD5DES"),
                new UsmUser(new OctetString("MD5DES"), AuthMD5.ID,
                        new OctetString("UserName"), PrivDES.ID,
                        new OctetString("PasswordUser")));
        snmp.getUSM().addUser(new OctetString("MD5DES"),
                new UsmUser(new OctetString("MD5DES"), null, null, null, null));
        snmp.listen();
    }
    
    // test: # snmptrap -c public -v 2c 127.0.0.1 "" 1.3.3.3.3.3.3.3 1.2.2.2.2.2.2 s "Aliens opened the door"
    @Override
    public void processPdu(CommandResponderEvent event) {
        StringBuffer msg = new StringBuffer("\n");
        //System.out.println("event = "+event.toString());
        VariableBinding[] myVB = event.getPDU().toArray();
        if (myVB != null && myVB.length > 0) {
            for (VariableBinding x : myVB) {
                if (x.toValueString().contains(":") && StringUtils.isNumeric(x.toValueString().replace(":", "9").replace(".", "9"))) {
                    msg.append("uptime = " + x.toValueString()).append(";\n");
                    continue;
                }
                if (x.toValueString().contains(".") && StringUtils.isNumeric(x.toValueString().replace(".", "9"))) {
                    msg.append("oid = " + x.toValueString()).append(";\n");
                    continue;
                }
                msg.append("message = " + x.toValueString()).append(";\n");
            }
            //Arrays.asList(myVB).stream().forEach(x -> msg.append(x.toValueString()).append(";\n"));
        }
        //System.out.println("event.getPDU() = "+event.getPDU().toString());
        System.out.println("Message Received: " + msg.toString());
        /*System.out.println("1 Message: " + msg.toString().split(";")[0]);
        System.out.println("2 Message: " + msg.toString().split(";")[1]);
        System.out.println("3 Message: " + msg.toString().split(";")[2]);*/
    }
    
}
