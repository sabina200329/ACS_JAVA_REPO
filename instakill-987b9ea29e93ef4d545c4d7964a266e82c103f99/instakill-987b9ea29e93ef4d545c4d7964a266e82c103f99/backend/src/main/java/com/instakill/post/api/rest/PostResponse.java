package com.instakill.post.api.rest;

import com.instakill.media.MediaTypes;
import com.instakill.user.api.rest.UserResponse;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID authorId,
        String content,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        long commentCount,
        boolean likedByCurrentUser,
        UserResponse author
) {
    /** True dacă media atașată este video (folosit în template-uri). */
    public boolean video() {
        return MediaTypes.isVideo(imageUrl);
    }
}
