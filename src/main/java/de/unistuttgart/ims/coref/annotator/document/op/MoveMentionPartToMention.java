package de.unistuttgart.ims.coref.annotator.document.op;

import  de.unistuttgart.ims.coref.annotator.api.v2.DetachedMentionPart;
import  de.unistuttgart.ims.coref.annotator.api.v2.Mention;

public class MoveMentionPartToMention extends MoveOperation<DetachedMentionPart, Mention> {
	Mention from, to;
	DetachedMentionPart part;

	public MoveMentionPartToMention(Mention target, DetachedMentionPart part) {
		super(part.getMention(), target, part);
	}

}