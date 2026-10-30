package org.son.haruharu.domain.midparticipation;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.son.haruharu.domain.midparticipation.business.MidParticipationService;
import org.son.haruharu.domain.midparticipation.implement.exception.*;
import org.son.haruharu.domain.room.business.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.domain.chat.business.ChatService;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MidParticipationPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name",POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto",()->"create-drop");
        r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired MidParticipationService service;
    @Autowired org.son.haruharu.domain.midparticipation.business.ParticipationAdminService admin;
    @Autowired RoomWriter rooms;
    @Autowired RoomReader roomReader;
    @Autowired RoomMemberReader members;
    @Autowired RoomMemberJoiner joiner;
    @Autowired RoomJoinService normalJoin;
    @Autowired RoomInviteService invites;
    @Autowired RoomMemberRemovalService removals;
    @Autowired UserJpaRepository users;
    @Autowired JdbcTemplate sql;
    @Autowired TransactionTemplate tx;
    @Autowired ChatService chats;
    @Autowired org.son.haruharu.domain.chat.business.ChatDeliveryService delivery;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.son.haruharu.domain.notification.business.VideoReactedEventListener reactionNotifications;
    @Autowired org.son.haruharu.domain.video.business.VideoReactionService reactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean org.son.haruharu.domain.notification.business.PushSendService push;

    private Long user() {
        return users.save(UserEntity.from(new User(null,UserStatus.ACTIVE,null))).toDomain().id();
    }
    private Room room(int memberCount) {
        var now=LocalDateTime.now();var start=now.minusDays(1).minusHours(1);
        var room=rooms.save(Room.createScheduled(start,now.plusDays(1),now.plusDays(1).plusHours(2),
                now.plusDays(1).plusHours(3),now.plusDays(2),3,RoomComposition.of(null,"중간 참여 검증",3,3)));
        tx.executeWithoutResult(s->{
            roomReader.findByIdForUpdate(room.id());
            for(int i=0;i<memberCount;i++) joiner.join(RoomMember.of(room.id(),user(),i%2==0?"MALE":"FEMALE",null,start));
        });
        chats.createGroupRoom(room.id());
        return room;
    }
    private long invitation(Long userId) { return service.inbox(userId,null,10).invitations().get(0).invitationId(); }

    @Test void oneCampaignSeatWithTwoPhysicalVacanciesAcceptsExactlyOneAndRetryIsIdempotent() throws Exception {
        var room=room(4);var candidates=List.of(user(),user(),user(),user());
        var campaign=admin.createCampaign(room.id(),1,null,candidates);
        var start=new CountDownLatch(1);var successes=new AtomicInteger();var rejected=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(4);var errors=new ConcurrentLinkedQueue<Throwable>();
        try {
            var tasks=new ArrayList<Future<?>>();
            for(Long u:candidates) {
                long id=invitation(u);
                tasks.add(pool.submit(()->{try { start.await();service.accept(u,id);successes.incrementAndGet(); }
                    catch(ParticipationUnavailableException e) {rejected.incrementAndGet();}
                    catch(Throwable e) {errors.add(e);} }));
            }
            start.countDown();for(var task:tasks) task.get(30,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(errors).isEmpty();assertThat(successes.get()).isEqualTo(1);assertThat(rejected.get()).isEqualTo(3);
        assertThat(members.countByRoomId(room.id())).isEqualTo(5);
        var recipient=admin.campaigns(room.id(),0,20).stream().filter(c->c.campaignId().equals(campaign.campaignId())).findFirst().orElseThrow()
                .recipients().stream().filter(r->r.status().equals("ACCEPTED")).findFirst().orElseThrow();
        service.accept(recipient.userId(),recipient.invitationId());
        assertThat(members.countByRoomId(room.id())).isEqualTo(5);
    }

    @Test void ordinaryInviteAndMidParticipationShareTheLastPhysicalSeat() throws Exception {
        var room=room(5);Long candidate=user(),web=user();service.preference(candidate,true);
        admin.createCampaign(room.id(),1,null,List.of(candidate));long id=invitation(candidate);
        String code=invites.issueFor(room.id(),6,null,"test").code();
        var start=new CountDownLatch(1);var success=new AtomicInteger();var rejected=new AtomicInteger();
        var errors=new ConcurrentLinkedQueue<Throwable>();var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{try {start.await();service.accept(candidate,id);success.incrementAndGet();} catch(ParticipationUnavailableException e){rejected.incrementAndGet();} catch(Throwable e){errors.add(e);}});
            var b=pool.submit(()->{try {start.await();normalJoin.join(web,code);success.incrementAndGet();} catch(ParticipationUnavailableException|org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException e){rejected.incrementAndGet();} catch(Throwable e){errors.add(e);}});
            start.countDown();a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertThat(errors).isEmpty();assertThat(success.get()).isEqualTo(1);assertThat(rejected.get()).isEqualTo(1);
        assertThat(members.countByRoomId(room.id())).isEqualTo(6);
    }

    @Test void anExistingSubscriptionLosesAccessAfterRemovalButOtherMembersStillReceive() {
        var room=room(3);var people=members.getByRoomId(room.id());var target=people.get(0);var other=people.get(1);
        long group=sql.queryForObject("select id from chat_rooms where room_id=? and type='GROUP'",Long.class,room.id());
        var registry=new org.son.haruharu.infrastructure.stomp.ChatSubscriptionRegistry(delivery,events);
        var guard=new org.son.haruharu.infrastructure.stomp.ChatOutboundChannelInterceptor(delivery,registry);
        for(var person:List.of(target,other)) {
            var h=org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE);
            h.setSessionId("qa-"+person.id());h.setSubscriptionId("sub");h.setDestination("/sub/chat-rooms/"+group);
            h.setUser(()->person.userId().toString());
            registry.registerSubscription(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],h.getMessageHeaders()));
        }
        var h=org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        h.setSessionId("qa-"+target.id());h.setSubscriptionId("sub");h.setDestination("/sub/chat-rooms/"+group);
        // Message author is deliberately different: the guard must use the recipient subscription.
        h.setUser(()->other.userId().toString());
        var packet=org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
        assertThat(guard.beforeHandle(packet,null,null)).isSameAs(packet);
        removals.remove(room.id(),target.id(),"미참여",1L);
        assertThat(guard.beforeHandle(packet,null,null)).isNull();
        h=org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        h.setSessionId("qa-"+other.id());h.setSubscriptionId("sub");h.setDestination("/sub/chat-rooms/"+group);
        var allowed=org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
        assertThat(guard.beforeHandle(allowed,null,null)).isSameAs(allowed);
    }

    @Test void removingAMemberWaitsForTheirInFlightChatAndHidesTheCommittedMessage() throws Exception {
        var room=room(3);var target=members.getByRoomId(room.id()).get(0);
        long group=sql.queryForObject("select id from chat_rooms where room_id=? and type='GROUP'",Long.class,room.id());
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Future<?> sender=null,remover=null;
        try {
            sender=pool.submit(()->tx.executeWithoutResult(status->{
                sql.queryForList("select id from chat_rooms where id=? for update",group);locked.countDown();
                try {if(!release.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                chats.sendMessage(group,target.userId(),"퇴장과 경합한 메시지");
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            remover=pool.submit(()->removals.remove(room.id(),target.id(),"미참여",1L));
            boolean waiting=false;
            for(int i=0;i<100;i++) {
                if(sql.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%chat_rooms%'",Long.class)>0){waiting=true;break;}
                Thread.sleep(10);
            }
            assertThat(waiting).isTrue();
        } finally {
            release.countDown();
            try {if(sender!=null)sender.get(30,TimeUnit.SECONDS);if(remover!=null)remover.get(30,TimeUnit.SECONDS);}
            finally {pool.shutdownNow();}
        }
        assertThat(sql.queryForObject("select count(*) from chat_messages where chat_room_id=? and sender_id=? and content='퇴장과 경합한 메시지' and member_removed_at is not null",Long.class,group,target.userId())).isEqualTo(1);
    }

    @Test void manualInvitationNeedsNoInterestRegistrationAndLaterOnlyMarksThePopupSeen() {
        var room=room(4);Long a=user(),b=user();
        var campaign=admin.createCampaign(room.id(),1,null,List.of(a,b));
        long aId=invitation(a),bId=invitation(b);
        assertThatThrownBy(()->service.invitation(a,bId)).isInstanceOf(ParticipationNotFoundException.class);
        assertThat(service.prompt(b).invitationId()).isEqualTo(bId);
        service.seen(b,bId,1);
        assertThat(service.prompt(b)).isNull();
        assertThat(service.invitation(b,bId).status()).isEqualTo("AVAILABLE");
        service.decline(a,aId);
        admin.resend(campaign.campaignId(),null,null);
        assertThat(service.invitation(a,aId).deliveryVersion()).isEqualTo(1);
        assertThat(service.invitation(b,bId).deliveryVersion()).isEqualTo(2);
        assertThat(service.invitation(b,bId).popupPending()).isTrue();
        service.seen(b,bId,1);assertThat(service.invitation(b,bId).popupPending()).isTrue();
        assertThat(service.accept(b,bId).roomId()).isEqualTo(room.id());
    }

    @Test void expiryAndFinalSelectionCannotBeBypassed() {
        var room=room(4);Long u=user();service.preference(u,true);
        var campaign=admin.createCampaign(room.id(),1,null,List.of(u));long id=invitation(u);
        sql.update("update mid_participation_campaigns set expires_at=? where id=?",LocalDateTime.now().minusMinutes(1),campaign.campaignId());
        assertThat(service.invitation(u,id).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(()->service.accept(u,id)).isInstanceOf(ParticipationUnavailableException.class);
        admin.resend(campaign.campaignId(),null,null);
        assertThat(service.invitation(u,id).status()).isEqualTo("AVAILABLE");
        sql.update("update rooms set final_selection_opens_at=? where id=?",LocalDateTime.now().minusSeconds(1),room.id());
        assertThatThrownBy(()->service.accept(u,id)).isInstanceOf(ParticipationUnavailableException.class);
        assertThat(service.invitation(u,id).popupPending()).isFalse();
    }

    @Test void inviteCodeCannotAddANewMemberAfterFinalSelectionOpens() {
        var room=room(5);Long newcomer=user();
        String code=invites.issueFor(room.id(),6,null,"test").code();
        sql.update("update rooms set final_selection_opens_at=? where id=?",LocalDateTime.now().minusMinutes(1),room.id());
        assertThatThrownBy(()->normalJoin.join(newcomer,code)).isInstanceOf(org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException.class);
        assertThat(members.countByRoomId(room.id())).isEqualTo(5);
    }
    @Test void removedMemberInboxLosesGroupChatNotificationsFromOthers() {
        var room=room(2);var target=members.getByRoomId(room.id()).get(0);
        long group=sql.queryForObject("select id from chat_rooms where room_id=? and type='GROUP'",Long.class,room.id());
        for(String type:List.of("CHAT","VIDEO_REPLY"))
            sql.update("insert into notification_history(user_id,type,title,body,payload_json,is_read,created_at,updated_at) values (?,?,'알림','유진님이 메시지를 보냈어요',?,false,?,?)",
                    target.userId(),type,"{\"chatRoomId\":\""+group+"\"}",LocalDateTime.now(),LocalDateTime.now());
        removals.remove(room.id(),target.id(),"미참여",1L);
        assertThat(sql.queryForObject("select count(*) from notification_history where user_id=? and member_removed_at is null",Long.class,target.userId())).isZero();
    }

    @Test void unlimitedInviteRoomKeepsAcceptingInviteCodesAfterRemoval() {
        var start=LocalDateTime.now().plusDays(1);
        var room=rooms.save(Room.createScheduled(start,start.plusDays(2),start.plusDays(2).plusHours(2),
                start.plusDays(2).plusHours(3),start.plusDays(3),3,RoomComposition.of(null,"무제한 코드",null,null)));
        String code=invites.issueFor(room.id(),null,null,"test").code();
        for(int i=0;i<3;i++) normalJoin.join(user(),code);
        removals.remove(room.id(),members.getByRoomId(room.id()).get(0).id(),"미참여",1L);
        normalJoin.join(user(),code);normalJoin.join(user(),code);
        assertThat(members.countByRoomId(room.id())).isEqualTo(4);
    }
    private Room roomWithoutGenderCapacity() {
        var start=LocalDateTime.now().minusHours(1);
        return rooms.save(Room.createScheduled(start,start.plusDays(2),start.plusDays(2).plusHours(2),
                start.plusDays(2).plusHours(3),start.plusDays(3),3,RoomComposition.of(null,"코드 정원",null,null)));
    }
    @Test void reissuedInviteLimitBecomesTheRoomCapacityAfterRemoval() {
        var room=roomWithoutGenderCapacity();
        String code=invites.issueFor(room.id(),3,null,"test").code();
        for(int i=0;i<3;i++) normalJoin.join(user(),code);
        removals.remove(room.id(),members.getByRoomId(room.id()).get(0).id(),"미참여",1L);
        String bigger=invites.reissueFor(room.id(),6,null,"test").code();
        normalJoin.join(user(),bigger);normalJoin.join(user(),bigger);
        assertThat(members.countByRoomId(room.id())).isEqualTo(4);
        assertThat(admin.availability(room.id()).capacity()).isEqualTo(6);
    }
    @Test void removedSeatInInviteLimitedRoomIsFilledByExactlyOneOffer() {
        var room=roomWithoutGenderCapacity();
        String code=invites.issueFor(room.id(),3,null,"test").code();
        for(int i=0;i<3;i++) normalJoin.join(user(),code);
        removals.remove(room.id(),members.getByRoomId(room.id()).get(0).id(),"미참여",1L);
        Long first=user(),second=user();
        admin.createCampaign(room.id(),1,null,List.of(first,second));
        service.accept(first,invitation(first));
        assertThatThrownBy(()->service.accept(second,invitation(second))).isInstanceOf(ParticipationUnavailableException.class);
        assertThat(members.countByRoomId(room.id())).isEqualTo(3);
    }

    @Test void reactionDoesNotHoldTheRoomLockWhileAuthorUploadHoldsTheMemberLock() throws Exception {
        var room=room(2);var participants=members.getByRoomId(room.id());var author=participants.get(0);var reactor=participants.get(1);
        long video=sql.queryForObject("insert into video_posts(room_member_id,slot_start,video_url,is_hidden,created_at) values (?,now(),'local-test-video',false,now()) returning id",Long.class,author.id());
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(3);
        try {
            var upload=pool.submit(()->tx.executeWithoutResult(s->{members.lockById(author.id());held.countDown();
                try {release.await(20,TimeUnit.SECONDS);} catch(InterruptedException e){throw new RuntimeException(e);}}));
            held.await();
            var reaction=pool.submit(()->reactions.toggle(reactor.userId(),video,org.son.haruharu.domain.video.implement.ReactionType.values()[0]));
            reaction.get(5,TimeUnit.SECONDS);
            assertThat(roomLockIsFree(room.id())).isTrue();
            release.countDown();upload.get(10,TimeUnit.SECONDS);
        } finally { release.countDown();pool.shutdownNow(); }
    }
    @Test void reactionWaitingForTheRoomLockFailsWhenTheAuthorIsRemovedMeanwhile() throws Exception {
        var room=room(2);var participants=members.getByRoomId(room.id());var author=participants.get(0);var reactor=participants.get(1);
        long video=sql.queryForObject("insert into video_posts(room_member_id,slot_start,video_url,is_hidden,created_at) values (?,now(),'local-test-video',false,now()) returning id",Long.class,author.id());
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var removal=pool.submit(()->tx.executeWithoutResult(s->{roomReader.findByIdForUpdate(room.id());held.countDown();
                try {release.await(20,TimeUnit.SECONDS);} catch(InterruptedException e){throw new RuntimeException(e);}
                removals.remove(room.id(),author.id(),"미참여",1L);}));
            held.await();
            var reaction=pool.submit(()->reactions.toggle(reactor.userId(),video,org.son.haruharu.domain.video.implement.ReactionType.values()[0]));
            Thread.sleep(500);release.countDown();removal.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->reaction.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(org.son.haruharu.common.exception.NotFoundException.class);
        } finally { release.countDown();pool.shutdownNow(); }
        assertThat(sql.queryForObject("select count(*) from video_reactions where video_post_id=?",Long.class,video)).isZero();
    }

    @Test void reactionPushIsSentAfterTheRoomLockIsReleased() {
        var room=room(2);var participants=members.getByRoomId(room.id());var author=participants.get(0);var reactor=participants.get(1);
        long video=sql.queryForObject("insert into video_posts(room_member_id,slot_start,video_url,is_hidden,created_at) values (?,now(),'local-test-video',false,now()) returning id",Long.class,author.id());
        var roomLockFree=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call->{roomLockFree.set(roomLockIsFree(room.id()));return null;}).when(push).sendToUsers(
                org.mockito.ArgumentMatchers.eq(List.of(author.userId())),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        reactionNotifications.handle(new org.son.haruharu.domain.video.business.event.VideoReactedEvent(author.userId(),reactor.userId(),video,"다른참가자","HEART"));
        assertThat(roomLockFree).isTrue();
    }
    private boolean roomLockIsFree(Long roomId) throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            return pool.submit(()->{
                try { tx.executeWithoutResult(s->sql.queryForList("select id from rooms where id=? for update nowait",roomId));return true; }
                catch(org.springframework.dao.DataAccessException e) { return false; }
            }).get(5,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    @Test void removalPreservesOriginalsHidesReferencesAndPreventsReentry() {
        var room=room(3);var participants=members.getByRoomId(room.id());var target=participants.get(0);var other=participants.get(1);
        long group=sql.queryForObject("select id from chat_rooms where room_id=? and type='GROUP'",Long.class,room.id());
        long message=sql.queryForObject("insert into chat_messages(chat_room_id,sender_id,sender_type,message_type,content,created_at,updated_at) values (?,?,'USER','TEXT','원본 보관',now(),now()) returning id",Long.class,group,target.userId());
        long reply=sql.queryForObject("insert into chat_messages(chat_room_id,sender_id,sender_type,message_type,content,reply_to_message_id,reply_preview_sender,reply_preview_content,created_at,updated_at) values (?,?,'USER','TEXT','상대방 본문',?,'이전가명','원본 보관',now(),now()) returning id",Long.class,group,other.userId(),message);
        long ownVideo=sql.queryForObject("insert into video_posts(room_member_id,slot_start,video_url,is_hidden,created_at) values (?,now(),'local-test-video',false,now()) returning id",Long.class,target.id());
        long otherVideo=sql.queryForObject("insert into video_posts(room_member_id,slot_start,video_url,is_hidden,created_at) values (?,now(),'local-test-video',false,now()) returning id",Long.class,other.id());
        String targetName=sql.queryForObject("select coalesce(pseudonym,'익명') from room_members where id=?",String.class,target.id());
        for (String payload : List.of("{\"videoPostId\":\""+ownVideo+"\"}",
                "{\"videoPostId\":\""+otherVideo+"\",\"reactor_user_id\":\""+target.userId()+"\"}",
                "{\"videoPostId\":\""+otherVideo+"\"}")) {
            sql.update("insert into notification_history(user_id,type,title,body,payload_json,is_read,created_at,updated_at) values (?,'REACTION_RECEIVED',?,'반응',?,false,now(),now())",other.userId(),"❤️ "+targetName+"님이 반응을 남겼어요",payload);
        }
        // 방보다 오래된 알림은 파싱하지 않는다. 깨진 옛 payload가 있어도 퇴장이 실패하지 않아야 한다.
        sql.update("insert into notification_history(user_id,type,title,body,payload_json,is_read,created_at,updated_at) values (?,'CHAT','옛 알림','옛 알림','not-json',false,?,?)",
                other.userId(),LocalDateTime.now().minusDays(30),LocalDateTime.now().minusDays(30));
        String code=invites.reissueFor(room.id(),6,null,"test").code();
        removals.remove(room.id(),target.id(),"미참여",1L);
        assertThat(members.findById(target.id())).isEmpty();
        assertThat(sql.queryForObject("select count(*) from notification_history where user_id=? and type='REACTION_RECEIVED' and member_removed_at is null",Long.class,other.userId())).isZero();
        assertThat(sql.queryForObject("select count(*) from notification_history where user_id=? and type='REACTION_RECEIVED' and member_removed_at is not null and deleted_at is null",Long.class,other.userId())).isEqualTo(3);
        reactionNotifications.handle(new org.son.haruharu.domain.video.business.event.VideoReactedEvent(other.userId(), target.userId(), otherVideo, targetName, "HEART"));
        reactionNotifications.handle(new org.son.haruharu.domain.video.business.event.VideoReactedEvent(target.userId(), other.userId(), ownVideo, "다른참가자", "HEART"));
        assertThat(sql.queryForObject("select count(*) from notification_history where user_id in (?,?) and type='REACTION_RECEIVED' and member_removed_at is null",Long.class,other.userId(),target.userId())).isZero();
        assertThat(sql.queryForObject("select content from chat_messages where id=?",String.class,message)).isEqualTo("원본 보관");
        assertThat(sql.queryForObject("select deleted_at is null and member_removed_at is not null from chat_messages where id=?",Boolean.class,message)).isTrue();
        var visible=chats.getMessages(group,other.userId(),null,50).messages();
        assertThat(visible).noneMatch(m->m.messageId().equals(message));
        var remaining=visible.stream().filter(m->m.messageId().equals(reply)).findFirst().orElseThrow();
        assertThat(remaining.content()).isEqualTo("상대방 본문");assertThat(remaining.replyTo()).isNull();
        assertThatThrownBy(()->normalJoin.join(target.userId(),code)).isInstanceOf(ParticipationUnavailableException.class);
        removals.remove(room.id(),target.id(),"재시도",1L);
        assertThat(sql.queryForObject("select count(*) from room_member_removals where member_id=?",Long.class,target.id())).isEqualTo(1);
    }
}
