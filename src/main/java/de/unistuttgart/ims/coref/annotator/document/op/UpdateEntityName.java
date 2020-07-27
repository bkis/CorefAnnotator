package de.unistuttgart.ims.coref.annotator.document.op;

import  de.unistuttgart.ims.coref.annotator.api.v2.Entity;

public class UpdateEntityName extends UpdateOperation<Entity> implements CoreferenceModelOperation {

	String newLabel;
	String oldLabel;

	public UpdateEntityName(Entity entity, String newName) {
		super(entity);
		this.oldLabel = entity.getLabel();
		this.newLabel = newName;
	}

	public Entity getEntity() {
		return this.getObjects().getFirst();
	}

	public String getNewLabel() {
		return newLabel;
	}

	public String getOldLabel() {
		return oldLabel;
	}

}