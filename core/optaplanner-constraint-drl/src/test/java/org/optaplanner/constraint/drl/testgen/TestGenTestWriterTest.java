/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.optaplanner.constraint.drl.testgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.optaplanner.constraint.drl.testgen.reproducer.TestGenCorruptedScoreException;
import org.optaplanner.core.api.score.buildin.simple.SimpleScore;
import org.optaplanner.core.impl.domain.variable.descriptor.VariableDescriptor;
import org.optaplanner.core.impl.score.buildin.SimpleScoreDefinition;
import org.optaplanner.core.impl.testdata.domain.TestdataEntity;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;
import org.optaplanner.core.impl.testdata.domain.TestdataValue;

class TestGenTestWriterTest {

    private static final String DRL_FILE_PLACEHOLDER = "SCORE_DRL_ABSOLUTE_PATH";
    private static final String DRL_FILE_PATH = "/x/y.drl";

    @Test
    @Timeout(600)
    void fullJournalOutput() throws IOException, URISyntaxException {
        TestGenKieSessionJournal journal = new TestGenKieSessionJournal();
        TestdataEntity entity = new TestdataEntity("E");
        TestdataValue value = new TestdataValue("V");
        journal.addFacts(Arrays.asList("abc", entity, value));
        journal.insertInitial(value);
        entity.setValue(value);
        VariableDescriptor<TestdataSolution> variableDescriptor = TestdataSolution.buildSolutionDescriptor()
                .getEntityDescriptorStrict(TestdataEntity.class)
                .getVariableDescriptor("value");
        journal.update(entity, variableDescriptor);
        journal.delete(value);
        journal.fireAllRules();

        TestGenTestWriter writer = new TestGenTestWriter();
        writer.setClassName("TestGenWriterOutput");
        writer.setScoreDefinition(new SimpleScoreDefinition());
        writer.setScoreDrlFileList(List.of(new File(DRL_FILE_PATH)));
        writer.setScoreDrlList(Arrays.asList("x", "y"));
        writer.setConstraintMatchEnabled(true);
        writer.setCorruptedScoreException(new TestGenCorruptedScoreException(
                SimpleScore.of(1), SimpleScore.of(0)));

        StringWriter sw = new StringWriter();
        writer.print(journal, sw);
        URL url = TestGenTestWriterTest.class.getResource("TestGenWriterOutput.java");
        checkOutput(Paths.get(url.toURI()), sw.toString());
    }

    @Test
    void emptyJournalOutput() {
        TestGenKieSessionJournal journal = new TestGenKieSessionJournal();
        TestGenTestWriter writer = new TestGenTestWriter();

        StringWriter sw = new StringWriter();
        writer.print(journal, sw);
        assertThat(sw.toString()).doesNotContain("import java.io.File;");

        // shouldn't throw exception even if lists are null
        writer.setScoreDrlFileList(null);
        writer.setScoreDrlList(null);
        writer.print(journal, new StringWriter());
    }

    @Test
    void dateFactField() {
        TestGenKieSessionJournal journal = new TestGenKieSessionJournal();
        TestClassWithDateField entity = new TestClassWithDateField();
        Date now = new Date();
        entity.setDate(now);
        journal.addFacts(List.of(entity));

        TestGenTestWriter writer = new TestGenTestWriter();
        writer.setClassName("TestGenWriterOutput");
        writer.setScoreDefinition(new SimpleScoreDefinition());

        StringWriter sw = new StringWriter();
        writer.print(journal, sw);
        String generatedCode = sw.toString();

        assertThat(generatedCode).contains(
                "import java.util.Date;",
                "setDate(new Date(" + now.getTime() + "));");
    }

    private static void checkOutput(Path expected, String actual) throws IOException {
        // first detect different lines
        List<String> expectedLines = Files.readAllLines(expected, StandardCharsets.UTF_8);
        List<String> actualLines = actual.lines().collect(Collectors.toList());
        SoftAssertions.assertSoftly(softly -> {
            for (int i = 0; i < Math.min(expectedLines.size(), actualLines.size()); i++) {
                String expectedLine = StringUtils.replace(expectedLines.get(i), DRL_FILE_PLACEHOLDER,
                        new File(DRL_FILE_PATH).getAbsolutePath());
                softly.assertThat(actualLines.get(i))
                        .withFailMessage("Mismatch at line %d.\n" +
                                "  Expected: '%s'.\n" +
                                "   But was: '%s'.", i + 1,
                                expected, actualLines.get(i))
                        .isEqualTo(expectedLine);
            }
        });

        // then check line counts are the same
        assertThat(actualLines).hasSameSizeAs(expectedLines);

        // finally check the whole string
        String expectedString = StringUtils.replace(Files.readString(expected, StandardCharsets.UTF_8), DRL_FILE_PLACEHOLDER,
                new File(DRL_FILE_PATH).getAbsolutePath());
        assertThat(actual).isEqualTo(expectedString);
    }

    static class TestClassWithDateField {

        private Date date;

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }
    }
}
