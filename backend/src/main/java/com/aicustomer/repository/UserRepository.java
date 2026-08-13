package com.aicustomer.repository;

import com.aicustomer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByTenantId(Long tenantId);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    /** 各租户用户数（平台账号 tenant_id 为 NULL 不参与）：返回 [tenantId, count] 行 */
    @Query("select u.tenantId, count(u) from User u where u.tenantId is not null group by u.tenantId")
    List<Object[]> countGroupByTenantId();
}
