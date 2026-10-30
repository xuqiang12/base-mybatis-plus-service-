package com.base.system.modules.ledger;

import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.ledger.mapper.CategoryMapper;
import com.base.system.modules.ledger.mapper.HouseholdItemMapper;
import com.base.system.modules.ledger.mapper.HouseholdMonthMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdServiceReadPathTest {
    @Test
    void existingCategoriesDoNotLockFamilyForReadSnapshot() {
        CategoryMapper categories = mock(CategoryMapper.class);
        HouseholdMonthMapper months = mock(HouseholdMonthMapper.class);
        HouseholdItemMapper items = mock(HouseholdItemMapper.class);
        FamilyMapper families = mock(FamilyMapper.class);
        MoneyService money = mock(MoneyService.class);
        when(categories.selectCount(any())).thenReturn(1L);
        when(categories.selectList(any())).thenReturn(List.of());
        when(months.selectList(any())).thenReturn(List.of());
        when(items.selectList(any())).thenReturn(List.of());
        when(money.projectTypes("family-1")).thenReturn(Map.of());

        HouseholdService service = new HouseholdService(categories, months, items, families, money, mock(org.springframework.jdbc.core.JdbcTemplate.class));

        HouseholdService.Snapshot snapshot = service.snapshot("family-1");

        assertThat(snapshot.categories()).isEmpty();
        verify(families, never()).lockFamily(any());
    }
}
