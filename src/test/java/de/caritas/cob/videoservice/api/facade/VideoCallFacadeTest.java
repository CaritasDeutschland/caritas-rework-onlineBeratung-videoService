package de.caritas.cob.videoservice.api.facade;

import static de.caritas.cob.videoservice.api.service.session.SessionStatus.IN_PROGRESS;
import static de.caritas.cob.videoservice.api.service.session.SessionStatus.NEW;
import static de.caritas.cob.videoservice.api.testhelper.TestConstants.CONSULTANT_ID;
import static de.caritas.cob.videoservice.api.testhelper.TestConstants.GROUP_CHAT_ID;
import static de.caritas.cob.videoservice.api.testhelper.TestConstants.SESSION_ID;
import static de.caritas.cob.videoservice.api.testhelper.TestConstants.USERNAME;
import static de.caritas.cob.videoservice.api.testhelper.TestConstants.VIDEO_CALL_UUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.videoservice.api.authorization.VideoUser;
import de.caritas.cob.videoservice.api.exception.httpresponse.BadRequestException;
import de.caritas.cob.videoservice.api.model.CreateVideoCallDTO;
import de.caritas.cob.videoservice.api.model.VideoCallResponseDTO;
import de.caritas.cob.videoservice.api.model.VideoRoomEntity;
import de.caritas.cob.videoservice.api.service.UuidRegistry;
import de.caritas.cob.videoservice.api.service.liveevent.LiveEventNotificationService;
import de.caritas.cob.videoservice.api.service.message.MessageService;
import de.caritas.cob.videoservice.api.service.session.ChatService;
import de.caritas.cob.videoservice.api.service.session.SessionService;
import de.caritas.cob.videoservice.api.service.statistics.StatisticsService;
import de.caritas.cob.videoservice.api.service.statistics.event.StartVideoCallStatisticsEvent;
import de.caritas.cob.videoservice.api.service.video.VideoCallUrlGeneratorService;
import de.caritas.cob.videoservice.api.service.video.VideoRoomService;
import de.caritas.cob.videoservice.api.service.video.jwt.model.VideoCallUrls;
import de.caritas.cob.videoservice.liveservice.generated.web.model.LiveEventMessage;
import de.caritas.cob.videoservice.liveservice.generated.web.model.VideoCallRequestDTO;
import de.caritas.cob.videoservice.statisticsservice.generated.web.model.UserRole;
import de.caritas.cob.videoservice.userservice.generated.web.model.ChatInfoResponseDTO;
import de.caritas.cob.videoservice.userservice.generated.web.model.ConsultantSessionDTO;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(SpringRunner.class)
public class VideoCallFacadeTest {

  @InjectMocks private VideoCallFacade videoCallFacade;
  @Mock private SessionService sessionService;
  @Mock private LiveEventNotificationService liveEventNotificationService;
  @Mock private VideoCallUrlGeneratorService videoCallUrlGeneratorService;
  @Mock private VideoUser authenticatedUser;
  @Mock private UuidRegistry uuidRegistry;
  @Mock private StatisticsService statisticsService;

  @Mock private VideoRoomService videoRoomService;

  @Mock private ChatService chatService;

  @Mock private MessageService messageService;

  @Test
  public void startVideoCall_Should_ReturnCorrectVideoCallUrl_When_UrlWasGeneratedSuccessfully() {
    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    ConsultantSessionDTO consultantSessionDto = mock(ConsultantSessionDTO.class);
    when(consultantSessionDto.getStatus()).thenReturn(IN_PROGRESS.getValue());
    VideoCallUrls videoCallUrls = new EasyRandom().nextObject(VideoCallUrls.class);

    when(sessionService.findSessionOfCurrentConsultant(SESSION_ID))
        .thenReturn(consultantSessionDto);
    when(videoCallUrlGeneratorService.generateVideoCallUrls(any())).thenReturn(videoCallUrls);

    // when
    VideoCallResponseDTO result =
        videoCallFacade.startVideoCall(new CreateVideoCallDTO().sessionId(SESSION_ID), "rcUserId");

    // then
    assertThat(result.getModeratorVideoCallUrl(), is(videoCallUrls.getModeratorVideoUrl()));
  }

  @Test
  public void
      startGroupVideoCall_Should_ReturnCorrectVideoCallUrl_When_UrlWasGeneratedSuccessfully() {

    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    VideoCallUrls videoCallUrls = new EasyRandom().nextObject(VideoCallUrls.class);
    when(chatService.findChatById(GROUP_CHAT_ID))
        .thenReturn(
            new EasyRandom()
                .nextObject(
                    de.caritas.cob.videoservice.userservice.generated.web.model.ChatInfoResponseDTO
                        .class));
    when(chatService.getChatMembers(GROUP_CHAT_ID))
        .thenReturn(
            new EasyRandom()
                .nextObject(
                    de.caritas.cob.videoservice.userservice.generated.web.model
                        .ChatMembersResponseDTO.class));
    when(videoCallUrlGeneratorService.generateVideoCallUrls(any())).thenReturn(videoCallUrls);

    // when
    VideoCallResponseDTO result =
        videoCallFacade.startVideoCall(
            new CreateVideoCallDTO().groupChatId(GROUP_CHAT_ID), "rcUserId");

    // then
    assertThat(result.getModeratorVideoCallUrl(), is(videoCallUrls.getModeratorVideoUrl()));
    verify(videoRoomService)
        .createGroupVideoRoom(
            Mockito.eq(GROUP_CHAT_ID), Mockito.eq(VIDEO_CALL_UUID), Mockito.anyString());
  }

  @Test(expected = AccessDeniedException.class)
  public void startGroupVideoCall_Should_ThrowForbiddenException_When_UserDoesNotHavePermissions() {
    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);

    doThrow(new AccessDeniedException("forbidden"))
        .when(chatService)
        .assertCanModerateChat(GROUP_CHAT_ID);

    // when
    videoCallFacade.startVideoCall(new CreateVideoCallDTO().groupChatId(GROUP_CHAT_ID), "rcUserId");
  }

  @Test
  public void
      joinGroupVideoCall_Should_ReturnCorrectVideoCallUrl_When_UrlWasGeneratedSuccessfully() {

    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);

    ChatInfoResponseDTO chat = new EasyRandom().nextObject(ChatInfoResponseDTO.class);
    when(chatService.findChatById(GROUP_CHAT_ID)).thenReturn(chat);

    VideoRoomEntity videoRoomEntity = new EasyRandom().nextObject(VideoRoomEntity.class);
    videoRoomEntity.setGroupChatId(GROUP_CHAT_ID);
    when(videoRoomService.findByJitsiRoomId(VIDEO_CALL_UUID))
        .thenReturn(Optional.of(videoRoomEntity));

    VideoCallUrls videoCallUrls = new EasyRandom().nextObject(VideoCallUrls.class);
    when(videoCallUrlGeneratorService.generateVideoCallUrls(any())).thenReturn(videoCallUrls);

    // when
    VideoCallResponseDTO result = videoCallFacade.joinGroupVideoCall(VIDEO_CALL_UUID);

    // then
    assertThat(result.getModeratorVideoCallUrl(), is(videoCallUrls.getModeratorVideoUrl()));
    verify(messageService)
        .createAndSendVideoChatJoinedMessage(
            chat.getGroupId(), authenticatedUser.getUsername(), videoRoomEntity);
  }

  @Test(expected = NoSuchElementException.class)
  public void joinGroupVideoCall_Should_ThrowNoSuchElementException_When_GivenChatNotFound() {
    // when
    videoCallFacade.joinGroupVideoCall(VIDEO_CALL_UUID);
  }

  @Test(expected = AccessDeniedException.class)
  public void joinGroupVideoCall_Should_ThrowForbiddenException_When_UserIsNotModerator() {
    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    VideoRoomEntity videoRoomEntity = new EasyRandom().nextObject(VideoRoomEntity.class);
    videoRoomEntity.setGroupChatId(GROUP_CHAT_ID);
    when(videoRoomService.findByJitsiRoomId(VIDEO_CALL_UUID))
        .thenReturn(Optional.of(videoRoomEntity));

    doThrow(new AccessDeniedException("forbidden"))
        .when(chatService)
        .assertCanModerateChat(GROUP_CHAT_ID);

    // when
    videoCallFacade.joinGroupVideoCall(VIDEO_CALL_UUID);
  }

  @Test
  public void
      startVideoCall_Should_CallLiveServiceAndBuildCorrectLiveEventMessageWithVideoCallRequestDto() {

    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    ConsultantSessionDTO consultantSessionDto =
        new EasyRandom().nextObject(ConsultantSessionDTO.class);
    consultantSessionDto.setStatus(IN_PROGRESS.getValue());
    VideoCallUrls videoCallUrls = new EasyRandom().nextObject(VideoCallUrls.class);

    when(sessionService.findSessionOfCurrentConsultant(SESSION_ID))
        .thenReturn(consultantSessionDto);
    when(videoCallUrlGeneratorService.generateVideoCallUrls(any())).thenReturn(videoCallUrls);
    when(authenticatedUser.getUsername()).thenReturn(USERNAME);
    ArgumentCaptor<LiveEventMessage> argument = ArgumentCaptor.forClass(LiveEventMessage.class);

    // when
    videoCallFacade.startVideoCall(new CreateVideoCallDTO().sessionId(SESSION_ID), "rcUserId");

    // then
    verify(liveEventNotificationService).sendVideoCallRequestLiveEvent(argument.capture(), any());
    verify(liveEventNotificationService, times(1)).sendVideoCallRequestLiveEvent(any(), any());
    assertThat(argument.getValue(), instanceOf(LiveEventMessage.class));
    var eventContent = (VideoCallRequestDTO) argument.getValue().getEventContent();
    assertNotNull(eventContent);
    assertEquals(consultantSessionDto.getGroupId(), eventContent.getRcGroupId());
    assertEquals("rcUserId", eventContent.getInitiatorRcUserId());
    assertEquals(USERNAME, eventContent.getInitiatorUsername());
  }

  @Test(expected = BadRequestException.class)
  public void startVideoCall_Should_throwBadRequestException_When_sessionIsNotInProgress() {
    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    ConsultantSessionDTO consultantSessionDto = mock(ConsultantSessionDTO.class);
    when(consultantSessionDto.getStatus()).thenReturn(NEW.getValue());
    when(sessionService.findSessionOfCurrentConsultant(SESSION_ID))
        .thenReturn(consultantSessionDto);

    // when
    videoCallFacade.startVideoCall(new CreateVideoCallDTO().sessionId(SESSION_ID), "");
  }

  @Test
  public void startVideoCall_Should_FireAssignSessionStatisticsEvent() {

    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    ConsultantSessionDTO consultantSessionDto = mock(ConsultantSessionDTO.class);
    when(consultantSessionDto.getStatus()).thenReturn(IN_PROGRESS.getValue());
    VideoCallUrls videoCallUrls = new EasyRandom().nextObject(VideoCallUrls.class);

    when(sessionService.findSessionOfCurrentConsultant(SESSION_ID))
        .thenReturn(consultantSessionDto);
    when(videoCallUrlGeneratorService.generateVideoCallUrls(any())).thenReturn(videoCallUrls);

    // when
    videoCallFacade.startVideoCall(new CreateVideoCallDTO().sessionId(SESSION_ID), "rcUserId");

    // then
    ArgumentCaptor<StartVideoCallStatisticsEvent> captor =
        ArgumentCaptor.forClass(StartVideoCallStatisticsEvent.class);
    verify(statisticsService, times(1)).fireEvent(captor.capture());
    String userId =
        Objects.requireNonNull(ReflectionTestUtils.getField(captor.getValue(), "userId"))
            .toString();
    assertThat(userId, is(CONSULTANT_ID));
    String userRole =
        Objects.requireNonNull(ReflectionTestUtils.getField(captor.getValue(), "userRole"))
            .toString();
    assertThat(userRole, is(UserRole.CONSULTANT.toString()));
    Long sessionId =
        Long.valueOf(
            Objects.requireNonNull(ReflectionTestUtils.getField(captor.getValue(), "sessionId"))
                .toString());
    assertThat(sessionId, is(SESSION_ID));
    String videoCallUuid =
        Objects.requireNonNull(ReflectionTestUtils.getField(captor.getValue(), "videoCallUuid"))
            .toString();
    assertThat(videoCallUuid, is(VIDEO_CALL_UUID));
  }

  @Test
  public void
      startVideoCall_Should_FireAssignSessionStatisticsEventWithDisplayName_When_initiatorDisplayNameIsSet() {

    // given
    when(authenticatedUser.getUserId()).thenReturn(CONSULTANT_ID);
    when(uuidRegistry.generateUniqueUuid()).thenReturn(VIDEO_CALL_UUID);
    ConsultantSessionDTO consultantSessionDto = mock(ConsultantSessionDTO.class);
    when(consultantSessionDto.getStatus()).thenReturn(IN_PROGRESS.getValue());
    VideoCallUrls videoCallUrls = new EasyRandom().nextObject(VideoCallUrls.class);

    when(sessionService.findSessionOfCurrentConsultant(SESSION_ID))
        .thenReturn(consultantSessionDto);
    when(videoCallUrlGeneratorService.generateVideoCallUrls(any())).thenReturn(videoCallUrls);

    // when
    videoCallFacade.startVideoCall(
        new CreateVideoCallDTO()
            .sessionId(SESSION_ID)
            .initiatorDisplayName("initiator display name"),
        "rcUserId");

    // then
    var argument = ArgumentCaptor.forClass(LiveEventMessage.class);
    verify(liveEventNotificationService).sendVideoCallRequestLiveEvent(argument.capture(), any());
    verify(liveEventNotificationService).sendVideoCallRequestLiveEvent(any(), any());
    assertThat(argument.getValue(), instanceOf(LiveEventMessage.class));
    var eventContent = (VideoCallRequestDTO) argument.getValue().getEventContent();
    assertNotNull(eventContent);
    assertThat(eventContent.getInitiatorUsername(), is("initiator display name"));
  }
}
