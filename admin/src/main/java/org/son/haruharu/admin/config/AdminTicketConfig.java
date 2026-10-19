package org.son.haruharu.admin.config;

import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReader;
import org.son.haruharu.domain.ticket.implement.TicketLedgerWriter;
import org.son.haruharu.domain.ticket.implement.TicketWalletReader;
import org.son.haruharu.domain.ticket.implement.TicketWalletWriter;
import org.son.haruharu.domain.ticket.storage.TicketLedgerRepositoryImpl;
import org.son.haruharu.domain.ticket.storage.TicketWalletRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 운영진 티켓 지급(#492)에 필요한 core ticket 빈만 명시적으로 배선한다.
 * 지갑 락·원장 기록 규칙을 admin 에서 다시 구현하지 않고 core TicketService 를 그대로 쓴다.
 */
@Configuration
@Import({TicketService.class, TicketWalletReader.class, TicketWalletWriter.class, TicketLedgerReader.class,
        TicketLedgerWriter.class, TicketWalletRepositoryImpl.class, TicketLedgerRepositoryImpl.class})
public class AdminTicketConfig {}
