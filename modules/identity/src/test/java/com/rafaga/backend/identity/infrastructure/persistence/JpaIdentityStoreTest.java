package com.rafaga.backend.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rafaga.backend.identity.domain.model.User;
import com.rafaga.backend.identity.domain.model.UserStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

class JpaIdentityStoreTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void omitsLowerAndStatusPredicatesWhenQueryIsNullOrBlank() {
        UserJpaRepository repository = mock(UserJpaRepository.class);
        when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        JpaIdentityStore store = new JpaIdentityStore(repository);

        store.searchUsers("tenant-1", null, null, PageRequest.of(0, 20));
        store.searchUsers("tenant-1", "   ", null, PageRequest.of(0, 20));

        ArgumentCaptor<Specification<User>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository, org.mockito.Mockito.times(2)).findAll(captor.capture(), eq(PageRequest.of(0, 20)));

        for (Specification<User> spec : captor.getAllValues()) {
            Root<User> root = mock(Root.class);
            CriteriaQuery<?> cq = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            Path tenantPath = mock(Path.class);
            Predicate tenantPredicate = mock(Predicate.class);
            Predicate combined = mock(Predicate.class);

            when(root.get("tenantId")).thenReturn(tenantPath);
            when(cb.equal(tenantPath, "tenant-1")).thenReturn(tenantPredicate);
            when(cb.and(any(Predicate[].class))).thenReturn(combined);

            assertThat(spec.toPredicate(root, cq, cb)).isSameAs(combined);
            verify(cb, never()).lower(any());
            verify(cb, never()).like(any(Expression.class), anyString());
            verify(root, never()).get("status");
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void appliesCaseInsensitiveLikeAndStatusPredicateWhenProvided() {
        UserJpaRepository repository = mock(UserJpaRepository.class);
        when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        JpaIdentityStore store = new JpaIdentityStore(repository);

        store.searchUsers("tenant-1", "  Ana  ", UserStatus.ACTIVE, PageRequest.of(0, 10));

        ArgumentCaptor<Specification<User>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), eq(PageRequest.of(0, 10)));

        Root<User> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path tenantPath = mock(Path.class);
        Path statusPath = mock(Path.class);
        Path usernamePath = mock(Path.class);
        Path emailPath = mock(Path.class);
        Expression<String> lowerUsername = mock(Expression.class);
        Expression<String> lowerEmail = mock(Expression.class);
        Predicate tenantPredicate = mock(Predicate.class);
        Predicate statusPredicate = mock(Predicate.class);
        Predicate usernameLike = mock(Predicate.class);
        Predicate emailLike = mock(Predicate.class);
        Predicate orPredicate = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);

        when(root.get("tenantId")).thenReturn(tenantPath);
        when(root.get("status")).thenReturn(statusPath);
        when(root.get("username")).thenReturn(usernamePath);
        when(root.get("email")).thenReturn(emailPath);
        when(cb.equal(tenantPath, "tenant-1")).thenReturn(tenantPredicate);
        when(cb.equal(statusPath, UserStatus.ACTIVE)).thenReturn(statusPredicate);
        when(cb.lower(usernamePath)).thenReturn(lowerUsername);
        when(cb.lower(emailPath)).thenReturn(lowerEmail);
        when(cb.like(lowerUsername, "%ana%")).thenReturn(usernameLike);
        when(cb.like(lowerEmail, "%ana%")).thenReturn(emailLike);
        when(cb.or(usernameLike, emailLike)).thenReturn(orPredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(combined);

        assertThat(captor.getValue().toPredicate(root, cq, cb)).isSameAs(combined);
        verify(cb).like(lowerUsername, "%ana%");
        verify(cb).like(lowerEmail, "%ana%");
    }
}
