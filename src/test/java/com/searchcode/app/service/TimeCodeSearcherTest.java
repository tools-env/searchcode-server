package com.searchcode.app.service;

import com.searchcode.app.dto.CodeIndexDocument;
import com.searchcode.app.dto.CodeResult;
import junit.framework.TestCase;
import org.eclipse.jetty.util.ConcurrentArrayQueue;

import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeCodeSearcherTest extends TestCase {

    public void testTimeIndexIndexCreationAndSearch() {
        try {
            String contents = "this is some code that should be found";

            CodeIndexDocument cid = new CodeIndexDocument("repoLocationRepoNameLocationFilename", "", "fileName", "fileLocation", "fileLocationFilename", "md5hash", "languageName", 0, contents, "repoRemoteLocation", "codeOwner");
            cid.setRevision("99a5a271063def87b2473be79ce6f840d42d1f95");
            cid.setYearMonthDay("20160101");

            Queue queue = new ConcurrentArrayQueue<CodeIndexDocument>();
            queue.add(cid);

            CodeIndexer.indexTimeDocuments(queue);

            TimeCodeSearcher cs = new TimeCodeSearcher();

            assertThat(cs.search("this", 0).getCodeResultList()).hasAtLeastOneElementOfType(CodeResult.class);
            assertThat(cs.search("code should", 0).getCodeResultList()).hasAtLeastOneElementOfType(CodeResult.class);
            assertThat(cs.search("should be found", 0).getCodeResultList()).hasAtLeastOneElementOfType(CodeResult.class);
        }
        catch(Exception ex) {
            assertTrue(false);
        }
    }
}
