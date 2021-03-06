package org.embulk.input.soql;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sforce.async.AsyncApiException;

import org.embulk.config.ConfigSource;
import org.embulk.exec.GuessExecutor;
import org.embulk.spi.Buffer;
import org.embulk.spi.Exec;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputPluginUtils
 */
public class InputPluginUtils
{
    private InputPluginUtils() {}

    public static JsonNode createGuessColums(JsonArray jsonArray)
    {
        Buffer sample = Buffer.copyOf(jsonArray.toString().getBytes());
        JsonNode columns = Exec.getInjector().getInstance(GuessExecutor.class)
                .guessParserConfig(sample, Exec.newConfigSource(), createGuessConfig(sample.array().length)).getObjectNode().get("columns");
        return columns;
    }

    public static void addRunRecord(
        Schema schema,
        PluginTask pluginTask,
        ForceClient forceClient,
        PageBuilder pageBuilder) throws AsyncApiException, InterruptedException, ExecutionException
    {
        JsonArray jsonArray = forceClient.query(pluginTask.getObject(), pluginTask.getSoql());
        Spliterator<JsonElement> spliterator = Spliterators.spliteratorUnknownSize(jsonArray.iterator(), 0);
        StreamSupport.stream(spliterator, false).forEach(je -> {
            JsonObject jsonObject = je.getAsJsonObject();
            schema.visitColumns(new SoqlColumnVisitor(jsonObject, pageBuilder, pluginTask));
            pageBuilder.addRecord();
        });
    }

    private static ConfigSource createGuessConfig(int bufferSize)
    {
        Logger logger =  LoggerFactory.getLogger(InputPluginUtils.class);
        logger.info("guess buffer size: " + Integer.toString(bufferSize));
        return Exec.newConfigSource()
                    .set("guess_plugins", ImmutableList.of("soql"))
                    .set("guess_sample_buffer_bytes", bufferSize);
    }
}
