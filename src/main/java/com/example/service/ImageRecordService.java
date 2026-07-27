package com.example.service;

import com.example.model.ImageRecord;
import com.example.repository.ImageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图片记录相关操作服务：批量保存等。
 * 依赖：如果项目存在 ImageRecordRepository (Spring Data)，将会使用它保存记录；
 * 如果不存在（repository 为 null），将会降级为日志打印（方便本地/测试时先运行）。
 */
@Service
public class ImageRecordService {

    private static final Logger log = LoggerFactory.getLogger(ImageRecordService.class);

    private final ImageRecordRepository repository;

    @Autowired(required = false)
    public ImageRecordService(ImageRecordRepository repository) {
        this.repository = repository; // can be null if repo bean not present
    }

    /**
     * 批量保存图片记录。调用方应保证传入的 records 非空。
     */
    public void saveBatch(List<ImageRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        if (repository != null) {
            repository.saveAll(records);
            log.info("Saved {} image records via repository", records.size());
        } else {
            // 降级处理：记录到日志，便于调试（生产环境请提供实际的 Repository/实现）
            log.warn("ImageRecordRepository not available — falling back to logging. Records: {}", records.size());
            for (ImageRecord r : records) {
                log.info("ImageRecord: {}", r);
            }
        }
    }
}
