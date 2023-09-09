// Copyright (c) 2023 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details. 

package io.synadia;

import io.nats.client.support.*;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.util.FlinkRuntimeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static io.nats.client.support.JsonUtils.beginJson;
import static io.nats.client.support.JsonUtils.endJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SerializersDeserializersTests extends TestBase {

    @Test
    public void testStringPayload() throws Exception {

        // validate works from construction
        StringPayloadDeserializer spdAscii = new StringPayloadDeserializer("ASCII");
        StringPayloadDeserializer spdUtf8 = new StringPayloadDeserializer();
        StringPayloadSerializer spsAscii = new StringPayloadSerializer("ASCII");
        StringPayloadSerializer spsUtf8 = new StringPayloadSerializer();
        validateStringPayload(spdAscii, spdUtf8, spsAscii, spsUtf8);

        // validate works after setCharsetName called
        spdAscii.setCharsetName("ASCII");
        spdUtf8.setCharsetName("UTF-8");
        spsAscii.setCharsetName("ASCII");
        spsUtf8.setCharsetName("UTF-8");
        validateStringPayload(spdAscii, spdUtf8, spsAscii, spsUtf8);

        // validate works after java serialization round trip
        spdAscii = (StringPayloadDeserializer)javaSerializeDeserializeObject(spdAscii);
        spdUtf8 = (StringPayloadDeserializer)javaSerializeDeserializeObject(spdUtf8);
        spsAscii = (StringPayloadSerializer)javaSerializeDeserializeObject(spsAscii);
        spsUtf8 = (StringPayloadSerializer)javaSerializeDeserializeObject(spsUtf8);
        validateStringPayload(spdAscii, spdUtf8, spsAscii, spsUtf8);
    }

    private static void validateStringPayload(StringPayloadDeserializer spdAscii,
                                              StringPayloadDeserializer spdUtf8,
                                              StringPayloadSerializer spsAscii,
                                              StringPayloadSerializer spsUtf8) {

        byte[] bytes = PLAIN_ASCII.getBytes();
        assertEquals(PLAIN_ASCII, spdAscii.getObject(bytes, null));
        assertEquals(PLAIN_ASCII, spdUtf8.getObject(bytes, null));

        bytes = spsAscii.getBytes(PLAIN_ASCII, null);
        assertEquals(PLAIN_ASCII, spdAscii.getObject(bytes, null));
        assertEquals(PLAIN_ASCII, spdUtf8.getObject(bytes, null));

        bytes = spsUtf8.getBytes(PLAIN_ASCII, null);
        assertEquals(PLAIN_ASCII, spdAscii.getObject(bytes, null));
        assertEquals(PLAIN_ASCII, spdUtf8.getObject(bytes, null));

        for (String su : UTF8_TEST_STRINGS) {
            bytes = su.getBytes(StandardCharsets.UTF_8);
            assertNotEquals(su, spdAscii.getObject(bytes, null));
            assertEquals(su, spdUtf8.getObject(bytes, null));

            bytes = spsUtf8.getBytes(su, null);
            assertNotEquals(su, spdAscii.getObject(bytes, null));
            assertEquals(su, spdUtf8.getObject(bytes, null));
        }
    }

    @Test
    public void testJsonSerializablePayload() throws Exception {
        JsonSerializablePayloadSerializer ser = new JsonSerializablePayloadSerializer();
        JsonSerializablePayloadDeserializer dser = new JsonSerializablePayloadDeserializer();

        for (String json : WORD_COUNT_JSONS) {
            JsonValue jv = JsonParser.parse(json);
            byte[] bytes = ser.getBytes(jv, null);
            String s = new String(bytes);
            assertEquals(json, s);

            s = dser.getObject(bytes, null).toJson();
            jv = JsonParser.parse(s);
            bytes = ser.getBytes(jv, null);
            s = new String(bytes);
            assertEquals(json, s);
        }
    }

    @Test
    public void testCustomPayload() {
        WordCountSerializer ser = new WordCountSerializer();
        WordCountDeserializer dser = new WordCountDeserializer();
        for (String json : WORD_COUNT_JSONS) {
            WordCount wc = new WordCount(json);
            byte[] bytes = ser.getBytes(wc, null);
            WordCount wc2 = new WordCount(bytes);
            assertEquals(wc, wc2);
            wc2 = dser.getObject(bytes, null);
            assertEquals(wc, wc2);
        }
    }

    static class WordCount implements JsonSerializable {
        public String word;
        public int count;

        public WordCount(byte[] json) {
            this(new String(json));
        }

        public WordCount(String json) {
            try {
                JsonValue jv = JsonParser.parse(json);
                word = JsonValueUtils.readString(jv, "word");
                count = JsonValueUtils.readInteger(jv, "count");
            }
            catch (Exception e) {
                throw new FlinkRuntimeException("Invalid Json: " + e);
            }
        }

        @Override
        public String toJson() {
            StringBuilder sb = beginJson();
            JsonUtils.addField(sb, "word", word);
            JsonUtils.addField(sb, "count", count);
            return endJson(sb).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WordCount wordCount = (WordCount) o;

            if (count != wordCount.count) return false;
            return Objects.equals(word, wordCount.word);
        }

        @Override
        public int hashCode() {
            int result = word != null ? word.hashCode() : 0;
            result = 31 * result + count;
            return result;
        }
    }

    static class WordCountSerializer implements PayloadSerializer<WordCount> {
        @Override
        public byte[] getBytes(WordCount input, SinkWriter.Context context) {
            return input.serialize();
        }
    }

    static class WordCountDeserializer implements PayloadDeserializer<WordCount> {
        @Override
        public WordCount getObject(byte[] input, SinkWriter.Context context) {
            return new WordCount(input);
        }
    }
}