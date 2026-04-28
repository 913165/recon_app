package com.recon.ingestion.kafka;

import com.recon.common.dto.FileArrivedEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileEventProducer {

    private static final Logger log = LoggerFactory.getLogger(FileEventProducer.class);

    private final KafkaTemplate<String, FileArrivedEvent> kafkaTemplate;

    @Value("${recon.kafka.topics.file-arrived}")
    private String topic;

    public void publishFileArrived(@NonNull FileArrivedEvent event) {
        kafkaTemplate.send(topic, event.fileId(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event for file {}", event.fileId(), ex);
            } else {
                log.info("Published file-arrived event for {}", event.fileId());
            }
        });
    }
}
