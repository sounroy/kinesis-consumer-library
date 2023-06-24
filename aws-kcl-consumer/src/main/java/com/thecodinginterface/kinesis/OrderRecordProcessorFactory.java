package com.thecodinginterface.kinesis;

import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

/**
 * Simple factor for providing instance of ShardRecordProcessor to the Scheduler
 * to lease to shards.
 */
public class OrderRecordProcessorFactory implements ShardRecordProcessorFactory {
    @Override
    public ShardRecordProcessor shardRecordProcessor() {
        return new OrderRecordProcessor();
    }
}
