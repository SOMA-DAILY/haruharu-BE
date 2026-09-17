package org.son.haruharu.admin.domain.room.application;

import org.son.haruharu.admin.domain.room.business.AdminRoomSelectionService;
import org.son.haruharu.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/rooms/{roomId}/selections")
public class AdminRoomSelectionController {
    private final AdminRoomSelectionService service;
    public AdminRoomSelectionController(AdminRoomSelectionService service) { this.service = service; }
    @GetMapping
    public ApiResponse<List<AdminRoomSelectionService.Row>> list(@PathVariable Long roomId) {
        return ApiResponse.ok(service.list(roomId));
    }
}
