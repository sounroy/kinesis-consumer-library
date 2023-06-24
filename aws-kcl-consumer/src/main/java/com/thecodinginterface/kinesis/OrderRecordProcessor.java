package com.thecodinginterface.kinesis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.exceptions.ThrottlingException;
import software.amazon.kinesis.lifecycle.events.*;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import java.io.IOException;

/**
 * Performs the act of processing Order records from the Kinesis Data Stream implementing
 * logic to calculate the average order size over 30 second time windows.
 */
public class OrderRecordProcessor implements ShardRecordProcessor {

    static final Logger logger = LogManager.getLogger(OrderRecordProcessor.class);

    private String shardId;
    private OrdersManager ordersMgr = new OrdersManager();

    static final long REPORTING_INTERVAL_MS = 30000L;
    private long nextReportingTimeMS;

    static final long MAX_RETRY_SLEEP_MS = 640000L;
    static final long DEFAULT_RETRY_SLEEP_MS = 10000L;
    private long retrySleepMs = DEFAULT_RETRY_SLEEP_MS;

    private final ObjectMapper objMapper = new ObjectMapper();

    /**
     * RecordProcessor instance is being initialized to begin receiving records for a given shard.
     */
    @Override
    public void initialize(InitializationInput initializationInput) {
        shardId = initializationInput.shardId();
        logger.info(String.format("Initialized shard %s @ sequence %s",
                                shardId, initializationInput.extendedSequenceNumber().toString()));
        nextReportingTimeMS = System.currentTimeMillis() + REPORTING_INTERVAL_MS;
    }

    /**
     * The main logic implementing method which iterates over each batch of records pulled
     * from the Kinesis Data Stream, aggregating them with the OrdersManager class and
     * initiating the average order size calculation for every reporting interval as well
     * as checkpointing progress on the stream of records to DynamoDB
     */
    @Override
    public void processRecords(ProcessRecordsInput processRecordsInput) {
        logger.info(String.format("Given %d records to process from shard %s", processRecordsInput.records().size(), shardId));

        // iterate over each record passed into method, adding them to the OrdersManager
        int i = 0;
        for (KinesisClientRecord record : processRecordsInput.records()) {
            byte[] byteArr = new byte[record.data().remaining()];
            record.data().get(byteArr);
            try {
                ordersMgr.addOrder(objMapper.readValue(byteArr, Order.class));
            } catch (IOException e) {
                logger.error(String.format("Shard %s failed to deserialize record", shardId), e);
            }
        }

        // when the reporting interval has elapsed then use OrdersManager to calculate the
        // average order size over the interval that just finished and log it then
        // set new reporting time to the next increment of reporting interval
        if (System.currentTimeMillis() > nextReportingTimeMS) {
            double avgOrderSize = ordersMgr.calcAverageOrderSize();
            nextReportingTimeMS = System.currentTimeMillis() + REPORTING_INTERVAL_MS;
            ordersMgr = new OrdersManager();
            logger.info(String.format("Shard %s Average Order $%.2f", shardId, avgOrderSize));

            // checkpointing after a given period of time implements at least once
            // processing semantics trading off throughput for the potential to process
            // duplicate order records
            checkpoint(processRecordsInput.checkpointer());
        }
    }

    /**
     * Called when lease (the assignment of a specific shard to this specific RecordProcessor instance)
     * has been revoked. This method is useful for communicating this information to outside systems or
     * simply logging such information.
     */
    @Override
    public void leaseLost(LeaseLostInput leaseLostInput) {
        logger.info(String.format("Shard %s lease lost", shardId));
    }

    /**
     * Called when the end of a shard has been reached. You must checkpoint here in order to finalize
     * processing the shard's records as well as to begin processing records from it's child shards.
     */
    @Override
    public void shardEnded(ShardEndedInput shardEndedInput) {
        logger.info(String.format("Shard %s end reached, doing final checkpoint", shardId));
        checkpoint(shardEndedInput.checkpointer());
    }

    /**
     * Scheduler has initiated shutdown and is giving RecordProcessor instance notice.
     */
    @Override
    public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
        logger.info(String.format("Shard %s Scheduler is shutting down, checkpointing", shardId));
        checkpoint(shutdownRequestedInput.checkpointer());
    }

    private void checkpoint(RecordProcessorCheckpointer checkpointer) {
        logger.info(String.format("Checkpointing shard %s", shardId));
        try {
            checkpointer.checkpoint();
        } catch (ShutdownException se) {
            // Ignore checkpoint if the processor instance has been shutdown (fail over).
            logger.info("Caught shutdown exception, skipping checkpoint.", se);
        } catch (ThrottlingException e) {
            logger.error("Throttling exception occurred", e);

            // Use a backoff and retry policy.
            if (retrySleepMs < MAX_RETRY_SLEEP_MS) {
                try {
                    Thread.sleep(retrySleepMs);
                    retrySleepMs = retrySleepMs * 2;
                    checkpoint(checkpointer);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                    logger.warn(String.format("Retry thread interrupted for shard %s and sleep %d", shardId, retrySleepMs));
                }
            } else {
                logger.error(String.format("Shard %s failed to perform checkpoint after backoff and retries exhausted", shardId));
                Runtime.getRuntime().halt(1);
            }
        } catch (InvalidStateException e) {
            // This indicates an issue with the DynamoDB table (check for insufficient provisioned IOPS).
            logger.error("Cannot save checkpoint to the DynamoDB table used by the Amazon Kinesis Client Library.", e);
        }
        retrySleepMs = DEFAULT_RETRY_SLEEP_MS;
    }
}
