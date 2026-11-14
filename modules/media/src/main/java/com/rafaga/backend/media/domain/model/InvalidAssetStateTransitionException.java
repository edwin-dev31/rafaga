package com.rafaga.backend.media.domain.model;

import java.time.Instant;
import java.util.UUID;

public class InvalidAssetStateTransitionException extends IllegalStateException {
    public InvalidAssetStateTransitionException(AssetStatus from, AssetStatus to) {
        super("Cannot transition MediaAsset from " + from + " to " + to);
    }
}
