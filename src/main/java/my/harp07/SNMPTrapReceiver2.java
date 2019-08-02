package my.harp07;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import static my.harp07.ISDTF.sdf;
import static my.harp07.ISDTF.stf;
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
import org.snmp4j.security.PrivAES128;
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

public class SNMPTrapReceiver2 implements CommandResponder {

    private MultiThreadedMessageDispatcher dispatcher;
    private Snmp snmp = null;
    private Address listenAddress;
    private ThreadPool threadPool;
    private String community="apelsin-mandarin";
    private int udp_port=162;

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
        listenAddress = GenericAddress.parse("udp:0.0.0.0/"+udp_port);
        TransportMapping<?> transport;
        if (listenAddress instanceof UdpAddress) {
            transport = new DefaultUdpTransportMapping((UdpAddress) listenAddress);
        } else {
            transport = new DefaultTcpTransportMapping((TcpAddress) listenAddress);
        }
        USM usm = new USM(
                SecurityProtocols.getInstance().addDefaultProtocols(),
                new OctetString(MPv3.createLocalEngineID()), 0);
        // SecurityProtocols.getInstance().addPrivacyProtocol(new PrivAES192());
        usm.setEngineDiscoveryEnabled(true);
        snmp = new Snmp(dispatcher, transport);
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
        snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3(usm));
        SecurityModels.getInstance().addSecurityModel(usm);
        snmp.getUSM().addUser(
                new OctetString("MD5DES"),
                new UsmUser(new OctetString("MD5DES"), AuthMD5.ID,
                        new OctetString("UserName"), PrivAES128.ID,
                        new OctetString("UserName")));
        snmp.listen();
    }

    // # snmptrap -c public -v 2c 127.0.0.1 "" 1.3.3.3.3.3.3.3 1.2.2.2.2.2.2 s "Aliens opened the door"
    // # snmptrap -c lookin -v 2c localhost '' 1.3.6.1.4.1.8072.2.3.0.1 1.3.6.1.4.1.8072.2.3.2.1 i 123456
    @Override
    public void processPdu(CommandResponderEvent event) {
        if (!new String(event.getSecurityName()).equals(community)) {
            System.out.println("!!! snmp-community mismatch from: " + event.getPeerAddress()+", must be="+community+", received="+new String(event.getSecurityName()));
            return;
        }
        StringBuffer msg = new StringBuffer("\n");
        //System.out.println("event = "+event.toString());
        VariableBinding[] myVB = event.getPDU().toArray();
        ModelSnmpTrap mst=new ModelSnmpTrap();        
        if (myVB != null && myVB.length > 0) {
            mst.setIp(StringUtils.substringBefore(event.getPeerAddress().toString(),"/"));
            mst.setCommunity(new String(event.getSecurityName()));
            mst.setDate(sdf.format(new Date()));
            mst.setTime(stf.format(new Date()));
            Arrays.asList(myVB).stream().forEach(x->msg.append(x.toString()).append("; \n"));
            /*for (VariableBinding x : myVB) {
                if (x.toValueString().contains(":") && StringUtils.isNumeric(x.toValueString().replace(":", "9").replace(".", "9"))) {
                    msg.append("uptime = " + x.toValueString()).append(";\n");
                    continue;
                }
                if (x.toValueString().contains(".") && StringUtils.isNumeric(x.toValueString().replace(".", "9"))) {
                    msg.append("oid = " + x.toValueString()).append(";\n");
                    mst.setOid(x.toValueString());
                    continue;
                }
                //msg.append("message = " + x.toValueString()).append(";\n");
                msg.append(x.toString()).append("; \n");
                
            }*/
            mst.setMsg(msg.toString());
        }
        System.out.println("\n=============");//Message Received: " + msg.toString());
        //System.out.println("event.getPeerAddress() = " + event.getPeerAddress());
        System.out.println("event.getSecurityLevel() = " + event.getSecurityLevel());
        System.out.println("event.getSecurityModel() = " + event.getSecurityModel());
        //System.out.println("event.getSecurityName() = " + new String(event.getSecurityName())); 
        System.out.println(mst);
    }

}
