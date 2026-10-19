package org.son.haruharu.admin.domain.ticket.business;

import org.son.haruharu.admin.domain.ticket.business.command.GrantTicketCommand;
import org.son.haruharu.admin.domain.ticket.business.dto.AdminTicketGrantResponse;
import org.son.haruharu.admin.domain.user.implement.AdminUserReader;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영진 티켓 지급(SUPER 권한 — AdminSecurityConfig에서 제한). 보상·CS 대응용 수동 지급이다. */
@Service
public class AdminTicketService {

    private final AdminUserReader adminUserReader;
    private final TicketService ticketService;

    public AdminTicketService(AdminUserReader adminUserReader, TicketService ticketService) {
        this.adminUserReader = adminUserReader;
        this.ticketService = ticketService;
    }

    /**
     * 유저에게 티켓을 지급한다. 누가·왜 줬는지는 원장(ADMIN_GRANT, ref=어드민 id, memo)에 남는다.
     * 없는 유저는 404. 탈퇴 유저는 지갑이 soft-delete되어 있어 역시 404로 거절된다.
     */
    @Transactional
    public AdminTicketGrantResponse grant(GrantTicketCommand command) {
        adminUserReader.getById(command.userId());
        ticketService.credit(TicketCreditCommand.adminGrant(
                command.userId(), command.amount(), command.adminId(), command.memo()));
        return new AdminTicketGrantResponse(
                command.userId(), command.amount(), ticketService.getWallet(command.userId()).balance());
    }
}
