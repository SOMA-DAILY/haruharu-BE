package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 실제 영상 답장 r 에서 시작해 reply_to 연결을 따라간 답글 트리의 Depth(최장 경로 길이)와 Width(직접 답글 수).
 * 입력은 관찰 기간 안의 메시지만 받는다(기간 밖·미래 대화는 호출자가 거른다).
 * <ul>
 *   <li>r → 상대 → 나 → 상대 는 depth 3. 분기가 있으면 가장 긴 경로.</li>
 *   <li>숨겨진(삭제) 메시지는 세지 않는다. 끝에 달린 것이면 그냥 빼고, 그 아래로 살아 있는 답글이 이어지면 연결이
 *       끊긴 것이라 미관측(CHAIN_BROKEN)이다.</li>
 *   <li>경로가 상한을 넘으면 미관측(DEPTH_LIMIT).</li>
 * </ul>
 */
public final class ReplyDepthCalculator {

    private ReplyDepthCalculator() {
    }

    public static ReplyDepthResult calculate(Long rootId, Collection<ReplyDepthMessage> messages, int maxDepth) {
        Map<Long, List<ReplyDepthMessage>> children = new HashMap<>();
        for (ReplyDepthMessage message : messages) {
            if (message.replyToMessageId() != null && !message.id().equals(rootId)) {
                children.computeIfAbsent(message.replyToMessageId(), ignored -> new ArrayList<>()).add(message);
            }
        }
        int depth = 0;
        int width = 0;
        Set<Long> visited = new HashSet<>(Set.of(rootId));
        Deque<long[]> stack = new ArrayDeque<>();
        stack.push(new long[] {rootId, 0});
        while (!stack.isEmpty()) {
            long[] current = stack.pop();
            for (ReplyDepthMessage child : children.getOrDefault(current[0], List.of())) {
                if (!visited.add(child.id())) {
                    continue;
                }
                if (child.excluded()) {
                    if (hasVisibleDescendant(child.id(), children, visited)) {
                        return ReplyDepthResult.unobserved(ReplyDepthResult.CHAIN_BROKEN);
                    }
                    continue;
                }
                int childDepth = (int) current[1] + 1;
                if (childDepth > maxDepth) {
                    return ReplyDepthResult.unobserved(ReplyDepthResult.DEPTH_LIMIT);
                }
                if (current[0] == rootId) {
                    width++;
                }
                depth = Math.max(depth, childDepth);
                stack.push(new long[] {child.id(), childDepth});
            }
        }
        return ReplyDepthResult.observed(depth, width);
    }

    private static boolean hasVisibleDescendant(Long id, Map<Long, List<ReplyDepthMessage>> children,
                                                Set<Long> visited) {
        Deque<Long> stack = new ArrayDeque<>(List.of(id));
        while (!stack.isEmpty()) {
            for (ReplyDepthMessage child : children.getOrDefault(stack.pop(), List.of())) {
                if (!visited.add(child.id())) {
                    continue;
                }
                if (!child.excluded()) {
                    return true;
                }
                stack.push(child.id());
            }
        }
        return false;
    }

    /**
     * 영상 작성자가 답장 r 의 답글 트리(관찰 기간 안, 숨겨진 메시지와 그 아래 제외) 안에서 한 번이라도 답했는지.
     * Depth·Width 와 따로 쓰는 보조 지표다.
     */
    public static boolean ownerReplied(Long rootId, Collection<ReplyDepthMessage> messages, Long ownerId) {
        Map<Long, List<ReplyDepthMessage>> children = new HashMap<>();
        for (ReplyDepthMessage message : messages) {
            if (message.replyToMessageId() != null && !message.id().equals(rootId)) {
                children.computeIfAbsent(message.replyToMessageId(), ignored -> new ArrayList<>()).add(message);
            }
        }
        Set<Long> visited = new HashSet<>(Set.of(rootId));
        Deque<Long> stack = new ArrayDeque<>(List.of(rootId));
        while (!stack.isEmpty()) {
            for (ReplyDepthMessage child : children.getOrDefault(stack.pop(), List.of())) {
                if (child.excluded() || !visited.add(child.id())) {
                    continue;
                }
                if (ownerId.equals(child.senderId())) {
                    return true;
                }
                stack.push(child.id());
            }
        }
        return false;
    }
}
