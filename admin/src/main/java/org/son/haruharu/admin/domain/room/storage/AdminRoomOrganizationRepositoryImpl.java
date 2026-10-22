package org.son.haruharu.admin.domain.room.storage;

import jakarta.persistence.*;

import org.son.haruharu.admin.domain.room.business.exception.AdminRoomGroupDuplicateException;
import org.son.haruharu.admin.domain.room.implement.*;
import org.son.haruharu.admin.domain.room.storage.exception.AdminRoomNotFoundException;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AdminRoomOrganizationRepositoryImpl implements AdminRoomOrganizationRepository {
    private final EntityManager em;

    public AdminRoomOrganizationRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    public List<AdminRoomGroup> groups(int offset, int limit) {
        return em
                .createQuery(
                        "from AdminRoomGroupEntity g order by g.id desc",
                        AdminRoomGroupEntity.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(AdminRoomGroupEntity::toDomain)
                .toList();
    }

    public long countGroups() {
        return em.createQuery("select count(g) from AdminRoomGroupEntity g", Long.class)
                .getSingleResult();
    }

    public Optional<AdminRoomGroup> group(Long id) {
        return Optional.ofNullable(em.find(AdminRoomGroupEntity.class, id))
                .map(AdminRoomGroupEntity::toDomain);
    }

    public AdminRoomGroup createGroup(String name) {
        // Serialize names as well as concurrent creation without depending on failed flush
        // recovery.
        // A unique constraint is the final guard; the service maps races to 409.
        if (em.createQuery(
                                "select count(g) from AdminRoomGroupEntity g where g.name=:name",
                                Long.class)
                        .setParameter("name", name)
                        .getSingleResult()
                > 0) throw new AdminRoomGroupDuplicateException();
        var e = new AdminRoomGroupEntity(name);
        em.persist(e);
        em.flush();
        return e.toDomain();
    }

    public Map<Long, AdminRoomOrganization> findAll(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return em
                .createQuery(
                        "from AdminRoomOrganizationEntity o where o.roomId in :ids",
                        AdminRoomOrganizationEntity.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .map(AdminRoomOrganizationEntity::toDomain)
                .collect(Collectors.toMap(AdminRoomOrganization::roomId, v -> v));
    }

    public void lockRooms(List<Long> ids) {
        var rows =
                em.createQuery(
                                "select r from RoomEntity r where r.id in :ids order by r.id",
                                RoomEntity.class)
                        .setParameter("ids", ids)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .getResultList();
        if (rows.size() != ids.size()) throw new AdminRoomNotFoundException();
    }

    public void save(AdminRoomOrganization v) {
        em.merge(new AdminRoomOrganizationEntity(v));
    }
}
