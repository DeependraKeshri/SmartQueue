package com.smartqueue.service;

import com.smartqueue.audit.AuditService;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.BusinessDtos.BusinessDto;
import com.smartqueue.dto.Mappers;
import com.smartqueue.entity.Business;
import com.smartqueue.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Platform-administrator operations across tenants. */
@Service
public class PlatformService {
    private final BusinessRepository businesses;
    private final AuditService audit;

    public PlatformService(BusinessRepository businesses, AuditService audit) {
        this.businesses = businesses;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BusinessDto> listBusinesses() {
        return businesses.findAllByOrderByNameAsc().stream().map(Mappers::business).toList();
    }

    @Transactional
    public BusinessDto setActive(Long businessId, boolean active) {
        Business b = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business not found"));
        b.setActive(active);
        audit.record(businessId, active ? "BUSINESS_ACTIVATED" : "BUSINESS_SUSPENDED", "BUSINESS", businessId, Map.of());
        return Mappers.business(b);
    }
}
