package com.gsgd.generic_erp.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gsgd.generic_erp.util.GlobalUtil;

public class UtilTest {
    Logger log = LoggerFactory.getLogger(UtilTest.class);

    @Test
    void testJoinOneSentence() {
        String sentence = ",PRD-2026-0010,Brand Strategy Workshop Classic,\"Facilitated workshop covering positioning, audience, and messaging. Suitable for individual and business customers.\",SERVICE,false,\"ACTIVE,DISCONTINUED,DRAFT\",4,true,STANDARD,SKU-SER-00010,Base Offering,true,1024.92,1905.06,CAD,,,,,,";
        String[] result = GlobalUtil.joinOneSentence(sentence);
        String sub = sentence.substring(164);
        log.error("result===>" + sub);
    }
}
