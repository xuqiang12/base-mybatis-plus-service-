package com.base.system.modules.ledger;

import com.base.system.modules.family.mapper.FamilyMapper;
import com.base.system.modules.friend.mapper.FriendMapper;
import com.base.system.modules.ledger.dto.LedgerResponses;
import com.base.system.modules.ledger.entity.LoanEntity;
import com.base.system.modules.ledger.mapper.BookMapper;
import com.base.system.modules.ledger.mapper.GiftMapper;
import com.base.system.modules.ledger.mapper.LoanMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerServiceReadPathTest {
    @Test
    void resolvesDefaultLoanProjectOnceForAllLegacyRows() throws Exception {
        BookMapper books = mock(BookMapper.class);
        GiftMapper gifts = mock(GiftMapper.class);
        LoanMapper loans = mock(LoanMapper.class);
        HouseholdService household = mock(HouseholdService.class);
        FriendMapper friends = mock(FriendMapper.class);
        FamilyMapper families = mock(FamilyMapper.class);
        MoneyService money = mock(MoneyService.class);
        when(books.selectList(any())).thenReturn(List.of());
        when(friends.selectList(any())).thenReturn(List.of());
        when(gifts.selectList(any())).thenReturn(List.of());
        LoanEntity first = new LoanEntity();
        first.setFamilyId("family-1");
        LoanEntity second = new LoanEntity();
        second.setFamilyId("family-1");
        when(loans.selectList(any())).thenReturn(List.of(first, second));
        when(money.resolveProject("family-1", null)).thenReturn(9L);

        LedgerService service = new LedgerService(books, gifts, loans, household, friends, families, money);
        Method method = LedgerService.class.getDeclaredMethod(
                "dataForFamily", String.class, HouseholdService.Snapshot.class);
        method.setAccessible(true);

        LedgerResponses.DataSet result = (LedgerResponses.DataSet) method.invoke(
                service, "family-1", new HouseholdService.Snapshot(List.of(), List.of()));

        assertThat(result.getLoanRecords()).hasSize(2)
                .allSatisfy(loan -> assertThat(loan.getProjectId()).isEqualTo(9L));
        verify(money, times(1)).resolveProject("family-1", null);
    }
}
