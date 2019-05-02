package mchorse.blockbuster.common.entity;

import java.util.List;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.GunInfo;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Gun projectile entity
 * 
 * This bad boy is responsible for being a gun projectile. It works in a 
 * similar fashion as a snowball, but holds 
 */
public class EntityGunProjectile extends EntityThrowable implements IEntityAdditionalSpawnData
{
    public GunInfo props;
    public AbstractMorph morph;
    public int timer;
    public int hits;

    public int updatePos;
    public double targetX;
    public double targetY;
    public double targetZ;

    public EntityGunProjectile(World worldIn)
    {
        this(worldIn, null, null);
    }

    public EntityGunProjectile(World worldIn, GunInfo props, AbstractMorph morph)
    {
        super(worldIn);

        this.props = props;
        this.morph = morph;
    }

    @Override
    public void onUpdate()
    {
        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;

        if (!this.worldObj.isRemote)
        {
            this.setFlag(6, this.isGlowing());
        }

        this.onEntityUpdate();

        if (this.throwableShake > 0)
        {
            --this.throwableShake;
        }

        Vec3d position = new Vec3d(this.posX, this.posY, this.posZ);
        Vec3d next = new Vec3d(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
        RayTraceResult result = this.worldObj.rayTraceBlocks(position, next);

        if (result != null)
        {
            next = new Vec3d(result.hitVec.xCoord, result.hitVec.yCoord, result.hitVec.zCoord);
        }

        Entity entity = null;
        List<Entity> list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().addCoord(this.motionX, this.motionY, this.motionZ).expandXyz(1.0D));
        double d0 = 0.0D;

        for (int i = 0; i < list.size(); ++i)
        {
            Entity current = list.get(i);

            if (current.canBeCollidedWith())
            {
                AxisAlignedBB box = current.getEntityBoundingBox().expandXyz(0.30000001192092896D);
                RayTraceResult ray = box.calculateIntercept(position, next);

                if (ray != null)
                {
                    double d1 = position.squareDistanceTo(ray.hitVec);

                    if (d1 < d0 || d0 == 0.0D)
                    {
                        entity = current;
                        d0 = d1;
                    }
                }
            }
        }

        if (entity != null)
        {
            result = new RayTraceResult(entity);
        }

        if (result != null)
        {
            if (result.typeOfHit == RayTraceResult.Type.BLOCK && this.worldObj.getBlockState(result.getBlockPos()).getBlock() == Blocks.PORTAL)
            {
                this.setPortal(result.getBlockPos());
            }
            else
            {
                if (!net.minecraftforge.common.ForgeHooks.onThrowableImpact(this, result)) this.onImpact(result);
            }
        }

        this.posX += this.motionX;
        this.posY += this.motionY;
        this.posZ += this.motionZ;
        float distance = MathHelper.sqrt_double(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.rotationYaw = (float) (MathHelper.atan2(this.motionX, this.motionZ) * (180D / Math.PI));

        for (this.rotationPitch = (float) (MathHelper.atan2(this.motionY, distance) * (180D / Math.PI)); this.rotationPitch - this.prevRotationPitch < -180.0F; this.prevRotationPitch -= 360.0F)
        {}

        while (this.rotationPitch - this.prevRotationPitch >= 180.0F)
        {
            this.prevRotationPitch += 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw < -180.0F)
        {
            this.prevRotationYaw -= 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw >= 180.0F)
        {
            this.prevRotationYaw += 360.0F;
        }

        this.rotationPitch = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * 0.2F;
        this.rotationYaw = this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * 0.2F;
        float friction = this.props == null ? 1 : this.props.friction;
        float gravity = this.getGravityVelocity();

        if (this.isInWater())
        {
            for (int j = 0; j < 4; ++j)
            {
                this.worldObj.spawnParticle(EnumParticleTypes.WATER_BUBBLE, this.posX - this.motionX * 0.25D, this.posY - this.motionY * 0.25D, this.posZ - this.motionZ * 0.25D, this.motionX, this.motionY, this.motionZ, new int[0]);
            }

            friction *= 0.8F;
        }

        this.motionX *= friction;
        this.motionY *= friction;
        this.motionZ *= friction;

        if (!this.hasNoGravity())
        {
            this.motionY -= gravity;
        }

        this.setPosition(this.posX, this.posY, this.posZ);
        this.updateProjectile();
    }

    private void updateProjectile()
    {
        if (this.worldObj.isRemote && this.updatePos > 0)
        {
            double d0 = this.posX + (this.targetX - this.posX) / this.updatePos;
            double d1 = this.posY + (this.targetY - this.posY) / this.updatePos;
            double d2 = this.posZ + (this.targetZ - this.posZ) / this.updatePos;

            this.updatePos--;
            this.setPosition(d0, d1, d2);
        }

        this.timer++;

        if (this.morph != null)
        {
            this.props.createEntity(this.worldObj);
            this.morph.update(this.props.entity, null);
        }

        if (this.props == null)
        {
            return;
        }

        /* Apply friction */
        float friction = this.props.friction;

        this.motionX *= friction;
        this.motionY *= friction;
        this.motionZ *= friction;

        if (this.timer > this.props.lifeSpan)
        {
            this.setDead();

            if (!this.worldObj.isRemote && !this.props.impactCommand.isEmpty())
            {
                this.getServer().commandManager.executeCommand(this, this.props.impactCommand);
            }
        }

        if (this.props.ticking > 0 && this.timer % this.props.ticking == 0)
        {
            if (!this.worldObj.isRemote && !this.props.tickCommand.isEmpty())
            {
                this.getServer().commandManager.executeCommand(this, this.props.tickCommand);
            }
        }
    }

    @Override
    protected float getGravityVelocity()
    {
        return this.props == null ? super.getGravityVelocity() : this.props.gravity;
    }

    @Override
    public void writeSpawnData(ByteBuf buffer)
    {
        buffer.writeBoolean(this.props != null);

        if (this.props != null)
        {
            ByteBufUtils.writeTag(buffer, this.props.toNBT());
        }

        buffer.writeBoolean(this.morph != null);

        if (this.morph != null)
        {
            NBTTagCompound tag = new NBTTagCompound();
            this.morph.toNBT(tag);
            ByteBufUtils.writeTag(buffer, tag);
        }
    }

    @Override
    public void readSpawnData(ByteBuf additionalData)
    {
        if (additionalData.readBoolean())
        {
            this.props = new GunInfo(ByteBufUtils.readTag(additionalData));
        }

        if (additionalData.readBoolean())
        {
            this.morph = MorphManager.INSTANCE.morphFromNBT(ByteBufUtils.readTag(additionalData));
        }
    }

    @Override
    protected void onImpact(RayTraceResult result)
    {
        this.hits++;

        if (this.props != null && this.timer >= 2)
        {
            boolean shouldDie = this.props.vanish && this.hits >= this.props.hits;

            if (result.typeOfHit == Type.BLOCK && this.props.bounce && !shouldDie)
            {
                Axis axis = result.sideHit.getAxis();

                if (axis == Axis.X) this.motionX *= -1;
                if (axis == Axis.Y) this.motionY *= -1;
                if (axis == Axis.Z) this.motionZ *= -1;
            }

            if (!this.worldObj.isRemote)
            {
                if (!this.props.impactCommand.isEmpty())
                {
                    this.getServer().commandManager.executeCommand(this, this.props.impactCommand);
                }

                if (result.typeOfHit == Type.ENTITY && this.props.damage > 0)
                {
                    result.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, null), this.props.damage);
                }

                if (shouldDie)
                {
                    this.setDead();
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport)
    {
        double dx = this.posX - x;
        double dy = this.posY - y;
        double dz = this.posZ - z;
        double dist = dx * dx + dy * dy + dz * dz;

        if (dist > 2 * 2)
        {
            this.updatePos = posRotationIncrements;
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound)
    {
        super.readEntityFromNBT(compound);

        this.setDead();
    }

    /**
     * Is projectile in range in render distance
     *
     * This method is responsible for checking if this entity is 
     * available for rendering. Rendering range is configurable.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public boolean isInRangeToRenderDist(double distance)
    {
        double d0 = Blockbuster.proxy.config.actor_rendering_range;
        return distance < d0 * d0;
    }
}