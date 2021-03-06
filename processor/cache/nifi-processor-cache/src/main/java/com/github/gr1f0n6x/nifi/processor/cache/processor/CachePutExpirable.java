package com.github.gr1f0n6x.nifi.processor.cache.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.gr1f0n6x.nifi.service.common.Deserializer;
import com.github.gr1f0n6x.nifi.service.common.ExpirableCache;
import com.github.gr1f0n6x.nifi.service.common.Serializer;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CachePutExpirable extends CachePut {
    private static List<PropertyDescriptor> descriptors;
    private static Set<Relationship> relationships;

    static {
        List<PropertyDescriptor> props = new ArrayList<>();
        props.add(EXPIRABLE_CACHE);
        props.add(KEY_FIELD);
        props.add(SERIALIZER);
        props.add(DESERIALIZER);
        props.add(TTL);
        props.add(BATCH_SIZE);
        descriptors = Collections.unmodifiableList(props);

        Set<Relationship> relations = new HashSet<>();
        relations.add(SUCCESS);
        relations.add(FAILURE);
        relationships = Collections.unmodifiableSet(relations);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        final int batch = context.getProperty(BATCH_SIZE).asInteger();

        final List<FlowFile> flowFiles = session.get(batch);
        if (flowFiles.isEmpty()) {
            return;
        }

        final ComponentLog logger = getLogger();
        final ExpirableCache cache = context.getProperty(CACHE).asControllerService(ExpirableCache.class);
        final String keyField = context.getProperty(KEY_FIELD).getValue();
        final Serializer<JsonNode> serializer = getSerializer(context);
        final Deserializer<JsonNode> deserializer = getDeserializer(context);
        final int seconds = Math.toIntExact(context.getProperty(TTL).asTimePeriod(TimeUnit.SECONDS));

        if (serializer == null || deserializer == null) {
            logger.error("Please, specify correct serializer/deserializer classes");
            session.transfer(flowFiles, FAILURE);
            return;
        }

        flowFiles.forEach(f -> {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                session.exportTo(f, bout);
                bout.close();

                JsonNode node = deserializer.deserialize(bout.toByteArray());
                if (node.hasNonNull(keyField)) {
                    if (seconds <= 0) {
                        cache.set(node.get(keyField), node, serializer, serializer);
                    } else {
                        cache.set(node.get(keyField), node, seconds, serializer, serializer);
                    }
                    session.transfer(f, SUCCESS);
                } else {
                    logger.error("Flowfile {} does not has key field: {}", new Object[]{f, keyField});
                    session.transfer(f, FAILURE);
                }
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage());
                session.transfer(f, FAILURE);
            }
        });
    }
}
