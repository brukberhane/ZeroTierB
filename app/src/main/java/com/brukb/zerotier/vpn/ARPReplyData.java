package com.brukb.zerotier.vpn;

import java.net.InetAddress;

/**
 * ARP 应答报文的所需数据。由于报文内容总是当前节点的 IP 与 MAC，因此仅记录应答报文目标的信息。
 */
public class ARPReplyData {
    public final long destMac;
    public final InetAddress destAddress;

    public ARPReplyData(long destMac, InetAddress destAddress) {
        this.destMac = destMac;
        this.destAddress = destAddress;
    }
}
