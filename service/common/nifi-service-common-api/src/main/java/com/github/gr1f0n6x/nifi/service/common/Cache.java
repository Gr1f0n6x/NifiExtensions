package com.github.gr1f0n6x.nifi.service.common;

import org.apache.nifi.controller.ControllerService;

import java.io.IOException;

public interface Cache extends ControllerService {
    <K> boolean exists(K key, Serializer<K> serializer) throws IOException;

    <K,V> void set(K key, V value, Serializer<K> kSerializer, Serializer<V> vSerializer) throws IOException;

    <K> void delete(K key, Serializer<K> serializer) throws IOException;

    <K, V> V get(K key, Serializer<K> kSerializer, Deserializer<V> vDeserializer) throws IOException;

}
