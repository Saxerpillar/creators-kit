package com.creatorskit;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Model;

/**
 * CKObject variant for face-trajectory projectiles.
 *
 * <p>The render path runs the standard CKObject animation pipeline first and
 * then obtains a vertex-cloned Model from {@link Client#applyTransformations}
 * so pitch can be written without touching the animated source. RuneLite 1.13
 * removed {@code mergeModels(Model...)}, so the old owned deep-copy path is no
 * longer available. The API still documents the returned model as shared and
 * only safe until the next {@code applyTransformations} call; the engine's
 * {@code RuneLiteObjectController} draw path calls {@link #getModel()} and
 * consumes it immediately, so the mutation is not retained across frames.
 *
 * <p>The base model is rebuilt privately when pitch is enabled. That keeps the
 * colour arrays of the animated output private to this CKObject (and makes the
 * same path safe if the source happens to be the static base model).
 */
@Getter
@Setter
public class ProjectileCKObject extends CKObject
{
    /** Pitch angle in radians. Positive = nose up. */
    private double pitchRadians = 0.0;

    /**
     * When false, {@link #getModel} skips the deep-copy + pitch path and
     * returns the standard CKObject animation result directly.
     */
    private boolean pitchEnabled = false;

    private final Client client;

    public ProjectileCKObject(Client client)
    {
        super(client);
        this.client = client;
    }

    @Override
    public void setModel(Model baseModel)
    {
        super.setModel(baseModel);
        if (pitchEnabled)
        {
            ensurePrivateBaseModel();
        }
    }

    public void setPitchEnabled(boolean pitchEnabled)
    {
        this.pitchEnabled = pitchEnabled;
        if (pitchEnabled)
        {
            ensurePrivateBaseModel();
        }
    }

    @Override
    public Model getModel()
    {
        // RuneLite 1.13 removed mergeModels(Model...). Rebuild this CKObject's
        // base from its unlit source first so the animation output's colour
        // arrays are private; applyTransformations can then give us a copy with
        // cloned vertices for the pitch mutation.
        if (pitchEnabled && pitchRadians != 0.0 && !ensurePrivateBaseModel())
        {
            return super.getModel();
        }

        Model sharedAnimated = super.getModel();
        if (!pitchEnabled || pitchRadians == 0.0 || sharedAnimated == null)
        {
            return sharedAnimated;
        }

        Model owned = client.applyTransformations(sharedAnimated, null, 0, null, 0);
        if (owned == null || owned == sharedAnimated)
        {
            return sharedAnimated;
        }

        float[] vy = owned.getVerticesY();
        float[] vz = owned.getVerticesZ();
        if (vy == null || vz == null || vy.length != vz.length || vy.length == 0)
        {
            return owned;
        }

        applyPitchToVerts(vy, vz, pitchRadians);
        owned.calculateBoundsCylinder();
        return owned;
    }

    private static void applyPitchToVerts(float[] vy, float[] vz, double pitchRadians)
    {
        // Rotate around the Y/Z centroid of the animated mesh so the pitch
        // pivots through the model's geometric center rather than the OSRS
        // origin (which would translate the whole projectile during pitch).
        double sumY = 0;
        double sumZ = 0;
        int n = vy.length;
        for (int i = 0; i < n; i++)
        {
            sumY += vy[i];
            sumZ += vz[i];
        }
        float cy = (float) (sumY / n);
        float cz = (float) (sumZ / n);

        double sinP = Math.sin(pitchRadians);
        double cosP = Math.cos(pitchRadians);
        for (int i = 0; i < n; i++)
        {
            double dy = vy[i] - cy;
            double dz = vz[i] - cz;
            vy[i] = (float) (dy * cosP - dz * sinP + cy);
            vz[i] = (float) (dy * sinP + dz * cosP + cz);
        }
    }
}
