package com.gsgd.generic_erp.service.customer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.dto.customer.AuditTaskBrief;
import com.gsgd.generic_erp.dto.customer.CustomerOrderView;
import com.gsgd.generic_erp.entity.customer.CustomerOrder;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;

import lombok.RequiredArgsConstructor;

/** Read side of the customer order list, joined with order audit status. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerOrderQueryService {

    private final CustomerOrderRepository orderRepository;
    private final AuditTaskRepository auditTaskRepository;

    /** Open orders with no order audit yet. */
    public Page<CustomerOrderView> pending(Pageable pageable) {
        return withAudit(orderRepository.findPendingReview(pageable));
    }

    public long pendingCount() {
        return orderRepository.countPendingReview();
    }

    /** All synced orders, optionally searched by order number or customer. */
    public Page<CustomerOrderView> all(String q, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.strip();
        return withAudit(orderRepository.search(term, pageable));
    }

    private Page<CustomerOrderView> withAudit(Page<CustomerOrder> page) {
        List<String> numbers = page.getContent().stream().map(CustomerOrder::getOrderNo).distinct().toList();
        Map<String, AuditTaskBrief> latest = numbers.isEmpty() ? Map.of()
                : auditTaskRepository.findBriefByOrderNumberIn(numbers).stream()
                        .collect(Collectors.toMap(AuditTaskBrief::orderNumber, Function.identity(),
                                (a, b) -> Comparator.comparingInt(AuditTaskBrief::round).compare(a, b) >= 0 ? a : b));
        return page.map(c -> CustomerOrderView.of(c, latest.get(c.getOrderNo())));
    }
}
