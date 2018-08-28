package com.icodici.universa.contract.jsapi;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JSApiUrlParser {

    private List<MaskStruct> allowedDomainMasks = new ArrayList<>();
    private List<MaskStruct> allowedIpMasks = new ArrayList<>();

    public boolean isUrlAllowed(String s) {
        try {
            String src = s;
            if (!src.startsWith("http"))
                src = "https://" + s;
            URL url = new URL(src);
            String domain = url.getHost();
            List<String> domainParts = new ArrayList<>(Arrays.asList(domain.split("\\.")));
            int port = ((url.getPort()==-1)?url.getDefaultPort():url.getPort());
            if (domainIsIp(domainParts)) {
                for (MaskStruct mask : allowedIpMasks)
                    if (isIpAllowedByMask(domainParts, port, mask))
                        return true;
            } else {
                for (MaskStruct mask : allowedDomainMasks)
                    if (isDomainAllowedByMask(domainParts, port, mask))
                        return true;
            }
            return false;
        } catch (MalformedURLException e) {
            System.err.println("JSApiUrlParser.isUrlAllowed exception: " + e);
            return false;
        }
    }

    private boolean isDomainAllowedByMask(List<String> domainParts, int port, MaskStruct mask) {
        for (int i = 0; i <= domainParts.size(); ++i) {
            int iDomain = domainParts.size() - 1 - i;
            int iMask = mask.domainParts.size() - 1 - i;
            if (iMask >= 0) {
                String mp = mask.domainParts.get(iMask);
                if ("*".equals(mp))
                    if (checkPort(port, mask.port))
                        return true;
                if (iDomain >= 0) {
                    String dp = domainParts.get(iDomain);
                    if (!dp.equals(mp) || !checkPort(port, mask.port))
                        return false;
                } else if (iDomain == -1) {
                    if (!".".equals(mp) || !checkPort(port, mask.port))
                        return false;
                    else
                        return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean isIpAllowedByMask(List<String> ipParts, int port, MaskStruct mask) {
        for (int i = 0; i < 4; ++i) {
            String ipp = ipParts.get(i);
            String mp = mask.domainParts.get(i);
            if (!"*".equals(mp)) {
                if (Integer.parseInt(ipp) != Integer.parseInt(mp))
                    return false;
            }
        }
        return checkPort(port, mask.port);
    }

    private MaskStruct getDomainMaskStruct(String mask) {
        MaskStruct res = new MaskStruct();
        List<String> parts0 = new ArrayList<>(Arrays.asList(mask.split(":")));
        res.domainParts = new ArrayList<>(Arrays.asList(parts0.get(0).split("\\.")));
        if (!"*".equals(res.domainParts.get(0)))
            res.domainParts.add(0, ".");
        if (parts0.size() > 1)
            res.port = parts0.get(1);
        else
            res.port = "def";
        return res;
    }

    private MaskStruct getIpMaskStruct(String mask) {
        MaskStruct res = new MaskStruct();
        List<String> parts0 = new ArrayList<>(Arrays.asList(mask.split(":")));
        res.domainParts = new ArrayList<>(Arrays.asList(parts0.get(0).split("\\.")));
        if (res.domainParts.size() != 4)
            throw new IllegalArgumentException("JSApiUrlParser.getIpMaskStruct: wrong IP mask '"+mask+"'");
        for (String part : res.domainParts) {
            if ("*".equals(part)) {
                continue;
            } else {
                int p = Integer.parseInt(part);
                if (!("" + p).equals(part) || p < 0 || p > 255)
                    throw new IllegalArgumentException("JSApiUrlParser.getIpMaskStruct: wrong IP mask '" + mask + "'");
            }
        }
        if (parts0.size() > 1)
            res.port = parts0.get(1);
        else
            res.port = "def";
        return res;
    }

    public void addUrlMask(String mask) {
        allowedDomainMasks.add(getDomainMaskStruct(mask));
    }

    public void addIpMask(String mask) {
        allowedIpMasks.add(getIpMaskStruct(mask));
    }

    private boolean checkPort(int port, String portMask) {
        if ("*".equals(portMask))
            return true;
        if ("def".equals(portMask)) {
            if (port == 80 || port == 443)
                return true;
            return false;
        }
        if ((""+port).equals(portMask))
            return true;
        return false;
    }

    private boolean domainIsIp(List<String> domainParts) {
        try {
            if (domainParts.size() == 4) {
                boolean res = true;
                for (String part : domainParts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        res = false;
                        break;
                    }
                    if (!(""+num).equals(part)) {
                        res = false;
                        break;
                    }
                }
                return res;
            }
        } catch (Exception e) {
            // do nothing
        }
        return false;
    }

    private class MaskStruct {
        List<String> domainParts;
        String port;

        @Override
        public String toString() {
            return domainParts.toString() + ":"+port;
        }
    }

}
