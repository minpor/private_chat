package chat.privatechat.config

import chat.privatechat.api.dto.ChatResponse
import chat.privatechat.api.dto.CommitDraftRequest
import chat.privatechat.api.dto.CreateDirectChatRequest
import chat.privatechat.api.dto.CreateDraftRequest
import chat.privatechat.api.dto.DraftResponse
import chat.privatechat.api.dto.LoginRequest
import chat.privatechat.api.dto.MessageAcceptedResponse
import chat.privatechat.api.dto.MessageListResponse
import chat.privatechat.api.dto.MessageResponse
import chat.privatechat.api.dto.RefreshRequest
import chat.privatechat.api.dto.RegisterRequest
import chat.privatechat.api.dto.SendMessageRequest
import chat.privatechat.api.dto.TokenResponse
import chat.privatechat.api.dto.UpsertDraftRequest
import chat.privatechat.api.dto.UserResponse
import chat.privatechat.api.ws.DraftPatchAckPayload
import chat.privatechat.api.ws.DraftStartedPayload
import chat.privatechat.api.ws.MessageAcceptedPayload
import chat.privatechat.api.ws.MessageNewPayload
import chat.privatechat.api.ws.WsEnvelope
import chat.privatechat.api.ws.WsErrorPayload
import chat.privatechat.infrastructure.jwt.JwtProperties
import chat.privatechat.infrastructure.nats.NatsProperties
import chat.privatechat.infrastructure.nats.OutboxProperties
import chat.privatechat.infrastructure.persistence.AppR2dbcProperties
import chat.privatechat.infrastructure.persistence.ReadReplicaProperties
import chat.privatechat.infrastructure.redis.ChatMemberCacheProperties
import chat.privatechat.infrastructure.redis.DraftProperties
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration
@RegisterReflectionForBinding(
    AppR2dbcProperties::class,
    ReadReplicaProperties::class,
    JwtProperties::class,
    NatsProperties::class,
    OutboxProperties::class,
    ChatMemberCacheProperties::class,
    DraftProperties::class,
    RegisterRequest::class,
    LoginRequest::class,
    RefreshRequest::class,
    TokenResponse::class,
    UserResponse::class,
    CreateDirectChatRequest::class,
    ChatResponse::class,
    SendMessageRequest::class,
    MessageAcceptedResponse::class,
    MessageResponse::class,
    MessageListResponse::class,
    CreateDraftRequest::class,
    UpsertDraftRequest::class,
    CommitDraftRequest::class,
    DraftResponse::class,
    WsEnvelope::class,
    WsErrorPayload::class,
    DraftStartedPayload::class,
    DraftPatchAckPayload::class,
    MessageAcceptedPayload::class,
    MessageNewPayload::class
)
class NativeReflectionConfig
