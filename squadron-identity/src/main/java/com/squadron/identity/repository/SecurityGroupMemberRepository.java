package com.squadron.identity.repository;

import com.squadron.identity.entity.SecurityGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityGroupMemberRepository extends JpaRepository<SecurityGroupMember, UUID> {

    List<SecurityGroupMember> findByGroupId(UUID groupId);

    List<SecurityGroupMember> findByMemberTypeAndMemberId(String memberType, UUID memberId);

    void deleteByGroupIdAndMemberTypeAndMemberId(UUID groupId, String memberType, UUID memberId);

    boolean existsByGroupIdAndMemberTypeAndMemberId(UUID groupId, String memberType, UUID memberId);
}
