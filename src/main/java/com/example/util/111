package com.example.cloudphone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CloudPhoneMaintenanceService {

    private final CloudPhoneClient cloudPhoneClient;
    private final CloudHangupService cloudHangupService;
    private final CloudPhoneSessionRepository sessionRepository;

    @Value("${cloud-phone.maintenance-enabled:false}")
    private boolean maintenanceEnabled;

    public CloudPhoneMaintenanceService(
            CloudPhoneClient cloudPhoneClient,
            CloudHangupService cloudHangupService,
            CloudPhoneSessionRepository sessionRepository) {
        this.cloudPhoneClient = cloudPhoneClient;
        this.cloudHangupService = cloudHangupService;
        this.sessionRepository = sessionRepository;
    }

    public LaunchResult launch(String userId) {
        if (maintenanceEnabled) {
            // 维护期间不调用云手机前置查询接口，直接启动云挂机。
            return cloudHangupService.startDefault(userId);
        }

        CloudPhoneDevice device = cloudPhoneClient.preQuery(userId);
        return LaunchResult.cloudPhone(device);
    }

    @Scheduled(fixedDelay = 30_000)
    public void kickUsersDuringMaintenance() {
        if (!maintenanceEnabled) {
            return;
        }

        List<CloudPhoneSession> sessions =
                sessionRepository.findActiveSessions();

        for (CloudPhoneSession session : sessions) {
            cloudPhoneClient.disconnect(session.getDeviceId());
        }
    }

    public ReconnectResult reconnect(String userId) {
        if (maintenanceEnabled) {
            // 当前提示语没有按照工作项要求实现。
            return ReconnectResult.rejected("云手机暂不可用");
        }

        return ReconnectResult.connected(
                cloudPhoneClient.reconnect(userId)
        );
    }
}
