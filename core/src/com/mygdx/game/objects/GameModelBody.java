package com.mygdx.game.objects;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.bullet.collision.Collision;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btTypedConstraint;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.mygdx.game.pathfinding.NavMeshGraphPath;

/**
 * Created by Johannes Sjolund on 10/18/15.
 */
public class GameModelBody extends GameModel {
	// Collision flags
	public final static short NONE_FLAG = 0;
	public final static short NAVMESH_FLAG = 1 << 6;
	public final static short PC_FLAG = 1 << 10;
	public final static short GROUND_FLAG = 1 << 8;
	public final static short OBJECT_FLAG = 1 << 9;
	public final static short ALL_FLAG = -1;

	private final static Vector3 localInertia = new Vector3();
	public final btRigidBody body;
	public final short belongsToFlag;
	public final short collidesWithFlag;
	public final btCollisionShape shape;
	public final btRigidBody.btRigidBodyConstructionInfo constructionInfo;
	public final PhysicsMotionState motionState;
	protected final float mass;
	private final Matrix4 matrix = new Matrix4();
	private final Vector3 goalDirection = new Vector3();
	private final Vector3 newVelocity = new Vector3();
	public Array<btTypedConstraint> constraints = new Array<btTypedConstraint>();
	public PathFindingData pathData;

	public GameModelBody(Model model,
						 String id,
						 Vector3 location,
						 Vector3 rotation,
						 Vector3 scale,
						 btCollisionShape shape,
						 float mass,
						 short belongsToFlag,
						 short collidesWithFlag,
						 boolean callback,
						 boolean noDeactivate) {

		super(model, id, location, rotation, scale);

		this.mass = mass;
		this.shape = shape;
		this.belongsToFlag = belongsToFlag;
		this.collidesWithFlag = collidesWithFlag;

		if (mass > 0f) {
			shape.calculateLocalInertia(mass, localInertia);
		} else {
			localInertia.set(0, 0, 0);

		}
		this.constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(
				mass, null, shape, localInertia);
		body = new btRigidBody(constructionInfo);


		if (mass > 0f) {
			motionState = new PhysicsMotionState(modelInstance.transform);
			body.setMotionState(motionState);
		} else {
			motionState = null;
		}

		body.setContactCallbackFlag(belongsToFlag);
		if (callback) {
			body.setCollisionFlags(body.getCollisionFlags()
					| btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK);
		}
		if (noDeactivate) {
			body.setActivationState(Collision.DISABLE_DEACTIVATION);
		}
		body.setWorldTransform(modelInstance.transform);
	}

	public void dispose() {
		super.dispose();
		// Let the calling class be responsible for shape dispose since it can be reused
		// shape.dispose();
		constructionInfo.dispose();
		motionState.dispose();
		body.dispose();
		if (motionState != null) {
			motionState.dispose();
		}
		for (btTypedConstraint constraint : constraints) {
			constraint.dispose();
		}
		constraints.clear();
	}

	@Override
	public void update(float deltaTime) {
		super.update(deltaTime);

		if (pathData == null) {
			return;
		}

		if (pathData.goalReached) {
			return;
		}

		if (pathData.currentGoal == null && pathData.path.size == 0) {
			pathData.goalReached = true;
			return;
		}

		body.getWorldTransform(matrix);
		matrix.getTranslation(pathData.currentPosition);
		pathData.posGroundRay.origin.set(pathData.currentPosition);

		if (pathData.currentGoal != null) {

			float yVelocity = body.getLinearVelocity().y;

			float xzDst = Vector2.dst2(pathData.currentGoal.x, pathData.currentGoal.z,
					pathData.currentPosition.x, pathData.currentPosition.z);

			if (xzDst < 0.1f) {
				// set new goal if not empty
				pathData.currentGoal = null;
				if (pathData.path.size > 0) {
					pathData.currentGoal = pathData.path.pop();
				} else {
					body.setLinearVelocity(newVelocity.set(0, yVelocity, 0));
					body.setAngularVelocity(Vector3.Zero);
				}

			} else {
				matrix.idt();
				goalDirection.set(pathData.currentPosition).sub(pathData.currentGoal).scl(-1, 0, 1).nor();
				matrix.setToLookAt(goalDirection, Vector3.Y);
				matrix.setTranslation(pathData.currentPosition);
				body.setWorldTransform(matrix);

				newVelocity.set(goalDirection.scl(1, 0, -1)).scl(pathData.moveSpeed);
				pathData.goalReached = false;

				newVelocity.y = yVelocity;
				body.setLinearVelocity(newVelocity);
			}
		}

	}

	protected class PhysicsMotionState extends btMotionState {
		public final Matrix4 transform;

		public PhysicsMotionState(Matrix4 transform) {
			this.transform = transform;
		}

		@Override
		public void getWorldTransform(Matrix4 worldTrans) {
			worldTrans.set(transform);
		}

		@Override
		public void setWorldTransform(Matrix4 worldTrans) {
			transform.set(worldTrans);
		}
	}

	public class PathFindingData {
		public Array<Vector3> path = new Array<Vector3>();
		public NavMeshGraphPath trianglePath = new NavMeshGraphPath();

		public Vector3 currentGoal = null;
		public Vector3 currentPosition = new Vector3();
		public Ray posGroundRay = new Ray(new Vector3(), new Vector3(0, -1, 0));

		public float moveSpeed = 1;
		public boolean goalReached = true;

		public PathFindingData(Vector3 initialPosition) {
			currentPosition.set(initialPosition);
			posGroundRay.origin.set(initialPosition);
		}

		public void setPath(Array<Vector3> newPath) {
			goalReached = false;
			path.clear();
			path.addAll(newPath);
			currentGoal = path.pop();
		}

		public void clearPath() {
			goalReached = true;
			path.clear();
			trianglePath.clear();
			currentGoal = null;
		}
	}
}
