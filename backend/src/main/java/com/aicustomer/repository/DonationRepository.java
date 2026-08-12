package com.aicustomer.repository;

import com.aicustomer.entity.Donation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    /** 捐助记录分页：按时间倒序 */
    Page<Donation> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
