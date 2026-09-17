package com.gsgd.generic_erp.util;

import java.util.ArrayList;
import java.util.List;

public class GlobalUtil {

    /**
     * Splits a comma-separated sentence while preserving commas inside
     * double-quoted values.
     *
     * <p>
     * For example, {@code joinOneSentence("A,\"B,C\",D")} returnscle
     * {@code ["A", "B,C", "D"]}. Use this method when comma-separated
     * input may contain quoted values with commas.
     * </p>
     *
     * @param join the comma-separated input string
     * @return the separated values with surrounding double quotes removed
     */
    public static String[] joinOneSentence(String join) {
        List<String> temp = new ArrayList<>();
        List<Integer> idxes = new ArrayList<>();
        String[] result = new String[join.split(",").length];
        int current = 0;
        while ((current = join.indexOf("\"", current + 1)) > 0) {
            idxes.add(current);
        }
        current = 0;
        if (idxes.size() > 0) {
            for (int i = 0; i <= idxes.size(); i++) {
                if (i == 0) {
                    temp.add(join.substring(0, idxes.get(i)));
                } else if (i == idxes.size()) {
                    int start = idxes.get(i - 1) + 1;
                    temp.add(join.substring(start + 1));
                } else {
                    temp.add(join.substring(idxes.get(i - 1), idxes.get(i) + 1));
                }
            }

            for (String sentence : temp) {
                if (!sentence.contains("\"")) {
                    String[] splited = sentence.split(",");
                    for (String s : splited) {
                        if (s.isEmpty())
                            result[current] = null;
                        else
                            result[current] = s;
                        ++current;
                    }
                } else {
                    result[current] = sentence.replace("\"", "");
                    ++current;
                }
            }
        } else {
            return join.split(",");
        }
        return result;
    }
}
