package org.son.haruharu.domain.appupdate.business.command;
public record UpdateAppUpdatePolicyCommand(String iosMinimumVersion, String androidMinimumVersion, long revision) {}
