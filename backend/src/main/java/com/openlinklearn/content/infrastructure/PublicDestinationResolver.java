package com.openlinklearn.content.infrastructure;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class PublicDestinationResolver {

    public InetAddress[] resolvePublicAddresses(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new UnknownHostException("The destination has no addresses.");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new UnknownHostException("The destination resolves to a prohibited address.");
            }
        }
        return addresses;
    }

    boolean isPublicAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPublicIpv4(bytes);
        }
        if (bytes.length != 16 || !isGlobalUnicastIpv6(bytes)) {
            return false;
        }

        boolean documentation = unsigned(bytes[0]) == 0x20 && unsigned(bytes[1]) == 0x01
                && unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8;
        boolean orchid = unsigned(bytes[0]) == 0x20 && unsigned(bytes[1]) == 0x01
            && unsigned(bytes[2]) == 0 && (unsigned(bytes[3]) & 0xf0) == 0x10;
        boolean sixToFour = unsigned(bytes[0]) == 0x20 && unsigned(bytes[1]) == 0x02;
        boolean teredo = unsigned(bytes[0]) == 0x20 && unsigned(bytes[1]) == 0x01
                && bytes[2] == 0 && bytes[3] == 0;
        boolean mappedIpv4 = allZero(bytes, 0, 10) && bytes[10] == (byte) 0xff
                && bytes[11] == (byte) 0xff;
        boolean compatibleIpv4 = allZero(bytes, 0, 12);
        return !documentation && !orchid && !sixToFour && !teredo
                && !((mappedIpv4 || compatibleIpv4) && !isPublicIpv4Tail(bytes));
    }

    private boolean isPublicIpv4(byte[] address) {
        int first = unsigned(address[0]);
        int second = unsigned(address[1]);
        int third = unsigned(address[2]);
        int fourth = unsigned(address[3]);
        return first != 0 && first != 10 && first != 127 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && (second == 0 || second == 168))
                && !(first == 192 && second == 88 && third == 99)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113)
                && !(first == 255 && second == 255 && third == 255 && fourth == 255);
    }

    private boolean isPublicIpv4Tail(byte[] address) {
        return isPublicIpv4(new byte[]{address[12], address[13], address[14], address[15]});
    }

    private boolean isGlobalUnicastIpv6(byte[] address) {
        return (unsigned(address[0]) & 0xe0) == 0x20
                && !addressIsLocal(address);
    }

    private boolean addressIsLocal(byte[] address) {
        try {
            InetAddress parsed = InetAddress.getByAddress(address);
            return parsed.isAnyLocalAddress() || parsed.isLoopbackAddress()
                    || parsed.isLinkLocalAddress() || parsed.isSiteLocalAddress()
                    || parsed.isMulticastAddress();
        } catch (UnknownHostException exception) {
            return true;
        }
    }

    private boolean allZero(byte[] address, int start, int end) {
        for (int index = start; index < end; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}