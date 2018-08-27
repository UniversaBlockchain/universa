package com.icodici.universa.contract.jsapi;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JSApiUrlParser {

    private List<MaskStruct> allowedMasks = new ArrayList<>();

    public boolean isUrlAllowed(String s) {
        try {
            String src = s;
            if (!src.startsWith("http"))
                src = "https://" + s;
            URL url = new URL(src);
            String domain = url.getHost();
            List<String> domainParts = new ArrayList<>(Arrays.asList(domain.split("\\.")));
            int port = ((url.getPort()==-1)?url.getDefaultPort():url.getPort());
            System.out.print("domain : " + domain + ":" + port + " ");
            for (MaskStruct mask : allowedMasks) {
                if (isDomainAllowedByMask(domainParts, port, mask))
                    return true;
            }
            return false;
//        } catch (URISyntaxException e) {
//            System.err.println("JSApiUrlParser.isUrlAllowed exception: " + e);
//            return false;
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

    private MaskStruct getMaskStruct(String mask) {
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

    public void addUrlMask(String mask) {
        allowedMasks.add(getMaskStruct(mask));
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

    private class MaskStruct {
        List<String> domainParts;
        String port;

        @Override
        public String toString() {
            return domainParts.toString() + ":"+port;
        }
    }

}
