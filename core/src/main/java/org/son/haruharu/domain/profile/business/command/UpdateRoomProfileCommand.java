package org.son.haruharu.domain.profile.business.command;

/** 방 온보딩 프로필 저장 입력(#455). 이 4칸만 바꾼다(닉네임, 지역, 하는 일, 키 등은 안 건드림). */
public record UpdateRoomProfileCommand(Integer birthYear, String mbti, String department, String intoNow) {}
