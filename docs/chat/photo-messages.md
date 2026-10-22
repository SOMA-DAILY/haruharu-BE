# Chat photo messages (#538)

- `POST /api/v1/chat-rooms/{id}/photo-messages`: authenticated multipart `file` (JPEG/PNG, 10MB,25MP), `request_id` (canonical UUID). One photo per message; clients may select up to10 and upload in order. Response uses the normal message envelope with `message_type:IMAGE`, `content:사진`, `client_request_id` and `photo:{key,url,width,height}`. Never retry already acknowledged photos with a fresh request ID.
- A request ID belongs to a chat and sender. Same ID+bytes returns the original message with no second upload/event. Different bytes with the same ID returns409 `CHAT_PHOTO_REQUEST_CONFLICT`.
- `GET /api/v1/chat-rooms/{id}/messages/{messageId}/photo` refreshes the short-lived image URL after checking active membership, DIRECT block/withdrawal, same-room ownership, sender block and message deletion.
- Message/reply lists include authorized photo metadata. STOMP broadcasts include only key/dimensions (`url:null`); each receiver requests its own URL. Clients hide deleted originals/quotes and use the immutable key for an in-memory image cache.
- Sender completion scrolls to latest; receiving while reading history preserves the reading position. Failed photos retain their original request ID and prepared file; retry/discard are per-photo actions. In-memory outgoing files are canceled/cleared on account change.

## Deployment

1. Run `scripts/sql/538-chat-photos-up.sql` against the target database before deploying compatible app/admin. It preserves existing TEXT/MISSION rows and request ID uniqueness. `verify.sql` checks shape. The down migration refuses once any photo is accepted; never delete user messages to force rollback.
2. Grant the backend runtime role the following **object-only** permissions for its configured media bucket. No public ACL or bucket-wide wildcard is needed. Mirror to development/staging and production roles separately; do not apply development credentials to production.
3. Deploy BE before the photo-enabled FE. Existing clients still display `사진` as the text fallback.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::<media-bucket>/chat-photos/*"
  }]
}
```

Development S3 PUT, GET, presigned GET and DELETE were verified on 2026-10-23 after adding the approved prefix-only development policy. Production runtime permissions must be configured separately before deployment. Actual FCM delivery also needs the configured development FCM service account.
