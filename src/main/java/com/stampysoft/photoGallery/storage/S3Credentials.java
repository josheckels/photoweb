package com.stampysoft.photoGallery.storage;

import com.stampysoft.util.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

/**
 * Which AWS identity the photo buckets are reached with, for both {@link S3Uploader} and
 * {@link S3ImagePresigner}.
 * <p>
 * Exists because {@link DefaultCredentialsProvider} resolves <em>environment variables before the profile
 * file</em>. On a machine that also does work in another AWS account that is the wrong way round: an exported
 * {@code AWS_ACCESS_KEY_ID} wins over {@code AWS_PROFILE}, so the gallery would quietly sign with whichever
 * identity the shell happened to be holding. The same applies to an IDE started from the desktop, which
 * inherits an environment that need not match any terminal's.
 * <p>
 * So when {@code S3Profile} names a profile, that profile is used and nothing else is consulted - not the
 * environment, not system properties, not an instance role. Being explicit is the entire point; a fallback
 * would reintroduce exactly the ambiguity this removes.
 * <p>
 * The profile name is not a secret and belongs in {@code src/config.properties} despite that file being in
 * git. The key material stays in {@code ~/.aws/credentials} (or wherever
 * {@code aws.sharedCredentialsFile} points), which is where {@link ProfileCredentialsProvider} reads it from.
 */
final class S3Credentials
{
    static final String PROFILE_PROPERTY = "S3Profile";

    private S3Credentials()
    {
    }

    /** The configured profile, or null when the ambient credential chain is being used. */
    static String configuredProfile(Configuration cfg)
    {
        String profile = cfg.getProperty(PROFILE_PROPERTY, null);
        return profile == null || profile.isBlank() ? null : profile.trim();
    }

    /**
     * Unset {@code S3Profile} keeps the old behaviour - the default chain, which is right on a machine with
     * only one AWS account in play, and is what the production server uses with a credentials file of its own.
     */
    static AwsCredentialsProvider provider(Configuration cfg)
    {
        String profile = configuredProfile(cfg);
        return profile == null ? DefaultCredentialsProvider.create() : ProfileCredentialsProvider.create(profile);
    }

    /** For the startup log, so it is obvious which identity is in play without guessing. */
    static String describe(Configuration cfg)
    {
        String profile = configuredProfile(cfg);
        return profile == null ? "default credential chain" : "profile '" + profile + "'";
    }
}
