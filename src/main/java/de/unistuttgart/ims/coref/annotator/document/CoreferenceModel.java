package de.unistuttgart.ims.coref.annotator.document;

import java.util.Comparator;
import java.util.Map;
import java.util.prefs.Preferences;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.factory.AnnotationFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.multimap.set.MutableSetMultimap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.factory.SortedSets;

import de.unistuttgart.ims.coref.annotator.Annotator;
import de.unistuttgart.ims.coref.annotator.ColorProvider;
import de.unistuttgart.ims.coref.annotator.Constants;
import de.unistuttgart.ims.coref.annotator.Defaults;
import de.unistuttgart.ims.coref.annotator.RangedHashSetValuedHashMap;
import de.unistuttgart.ims.coref.annotator.Span;
import de.unistuttgart.ims.coref.annotator.Util;
import de.unistuttgart.ims.coref.annotator.api.v1.Comment;
import de.unistuttgart.ims.coref.annotator.api.v1.DetachedMentionPart;
import de.unistuttgart.ims.coref.annotator.api.v1.Entity;
import de.unistuttgart.ims.coref.annotator.api.v1.EntityGroup;
import de.unistuttgart.ims.coref.annotator.api.v1.Mention;
import de.unistuttgart.ims.coref.annotator.document.Event.Type;
import de.unistuttgart.ims.coref.annotator.document.op.AddEntityToEntityGroup;
import de.unistuttgart.ims.coref.annotator.document.op.AddMentionsToEntity;
import de.unistuttgart.ims.coref.annotator.document.op.AddMentionsToNewEntity;
import de.unistuttgart.ims.coref.annotator.document.op.AttachPart;
import de.unistuttgart.ims.coref.annotator.document.op.CoreferenceModelOperation;
import de.unistuttgart.ims.coref.annotator.document.op.GroupEntities;
import de.unistuttgart.ims.coref.annotator.document.op.MergeEntities;
import de.unistuttgart.ims.coref.annotator.document.op.MoveMentionPartToMention;
import de.unistuttgart.ims.coref.annotator.document.op.MoveMentionsToEntity;
import de.unistuttgart.ims.coref.annotator.document.op.Operation;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveDuplicateMentionsInEntities;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveEntities;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveEntitiesFromEntityGroup;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveMention;
import de.unistuttgart.ims.coref.annotator.document.op.RemovePart;
import de.unistuttgart.ims.coref.annotator.document.op.RemoveSingletons;
import de.unistuttgart.ims.coref.annotator.document.op.RenameAllEntities;
import de.unistuttgart.ims.coref.annotator.document.op.ToggleGenericFlag;
import de.unistuttgart.ims.coref.annotator.document.op.UpdateEntityColor;
import de.unistuttgart.ims.coref.annotator.document.op.UpdateEntityKey;
import de.unistuttgart.ims.coref.annotator.document.op.UpdateEntityName;
import de.unistuttgart.ims.coref.annotator.uima.AnnotationComparator;
import de.unistuttgart.ims.uimautil.AnnotationUtil;

/**
 * Class represents the document and the tree view on the document. All
 * annotation happens through this class.
 * 
 *
 */
public class CoreferenceModel extends SubModel implements Model {

	public static enum EntitySorter {
		LABEL, CHILDREN, COLOR
	}

	/**
	 * A mapping from character positions to annotations
	 */
	RangedHashSetValuedHashMap<Annotation> characterPosition2AnnotationMap = new RangedHashSetValuedHashMap<Annotation>();

	/**
	 * Assigns colors to new entities
	 */
	ColorProvider colorMap = new ColorProvider();

	HashSetValuedHashMap<FeatureStructure, Comment> comments = new HashSetValuedHashMap<FeatureStructure, Comment>();

	/**
	 * A list of listeners to annotation events
	 */
	MutableList<CoreferenceModelListener> crModelListeners = Lists.mutable.empty();

	MutableSetMultimap<Entity, Mention> entityMentionMap = Multimaps.mutable.set.empty();

	MutableSetMultimap<Entity, EntityGroup> entityEntityGroupMap = Multimaps.mutable.set.empty();

	Map<Character, Entity> keyMap = Maps.mutable.empty();

	/**
	 * The document
	 */
	@Deprecated
	JCas jcas;

	public CoreferenceModel(DocumentModel documentModel) {
		super(documentModel);
		this.jcas = documentModel.getJcas();
	}

	/**
	 * Create a new entity e and a new mention m, and add m to e. Does not fire any
	 * events.
	 * 
	 * @param begin
	 *            Begin of mention
	 * @param end
	 *            End of mention
	 * @return The new mention
	 */
	private Mention add(int begin, int end) {
		Annotator.logger.entry(begin, end);
		// document model
		Mention m = createMention(begin, end);
		Entity e = createEntity(m.getCoveredText());
		m.setEntity(e);
		entityMentionMap.put(e, m);

		return m;
	}

	private Mention add(Span selection) {
		return add(selection.begin, selection.end);
	}

	public boolean addCoreferenceModelListener(CoreferenceModelListener e) {
		e.entityEvent(Event.get(this, Event.Type.Init));
		return crModelListeners.add(e);
	}

	/**
	 * does not fire events
	 * 
	 * @param e
	 * @param begin
	 * @param end
	 * @return
	 */
	private Mention addTo(Entity e, int begin, int end) {
		Mention m = createMention(begin, end);
		m.setEntity(e);
		entityMentionMap.put(e, m);

		return m;
	}

	/**
	 * does not fire events
	 * 
	 * @param e
	 * @param span
	 * @return
	 */
	private Mention addTo(Entity e, Span span) {
		return addTo(e, span.begin, span.end);
	}

	/**
	 * does not fire events
	 * 
	 * @param m
	 * @param begin
	 * @param end
	 * @return
	 */
	private DetachedMentionPart addTo(Mention m, int begin, int end) {
		// document model
		DetachedMentionPart d = createDetachedMentionPart(begin, end);
		d.setMention(m);
		m.setDiscontinuous(d);

		return d;
	}

	private DetachedMentionPart addTo(Mention m, Span sp) {
		return addTo(m, sp.begin, sp.end);
	}

	protected DetachedMentionPart createDetachedMentionPart(int b, int e) {
		DetachedMentionPart dmp = AnnotationFactory.createAnnotation(jcas, b, e, DetachedMentionPart.class);
		if (getPreferences().getBoolean(Constants.CFG_TRIM_WHITESPACE, true))
			dmp = AnnotationUtil.trim(dmp);
		registerAnnotation(dmp);
		return dmp;
	}

	protected Entity createEntity(String l) {
		Entity e = new Entity(jcas);
		e.setColor(colorMap.getNextColor().getRGB());
		e.setLabel(l);
		e.setFlags(new StringArray(jcas, 0));
		e.addToIndexes();
		return e;
	}

	protected EntityGroup createEntityGroup(String l, int initialSize) {
		EntityGroup e = new EntityGroup(jcas);
		e.setColor(colorMap.getNextColor().getRGB());
		e.setLabel(l);
		e.setFlags(new StringArray(jcas, 0));
		e.addToIndexes();
		e.setMembers(new FSArray(jcas, initialSize));
		return e;
	}

	protected String createEntityGroupLabel(ImmutableList<Entity> entityList) {
		String s = entityList.subList(0, 2).select(e -> e.getLabel() != null)
				.collect(
						e -> StringUtils.abbreviate(e.getLabel(), "…", (Constants.UI_MAX_STRING_WIDTH_IN_TREE / 2) - 4))
				.makeString(" " + Annotator.getString(Constants.Strings.ENTITY_GROUP_AND) + " ");
		if (entityList.size() > 2)
			s += " + " + String.valueOf(entityList.size() - 2);
		return s;
	}

	/**
	 * Creates a new mention annotation in the document and adds it to the indexes
	 * 
	 * @param b
	 *            the begin character position
	 * @param e
	 *            the end character position
	 * @return the created mention
	 */
	protected Mention createMention(int b, int e) {
		Mention m = AnnotationFactory.createAnnotation(jcas, b, e, Mention.class);
		if (getPreferences().getBoolean(Constants.CFG_TRIM_WHITESPACE, Defaults.CFG_TRIM_WHITESPACE))
			m = AnnotationUtil.trim(m);
		if (getPreferences().getBoolean(Constants.CFG_FULL_TOKENS, Defaults.CFG_FULL_TOKENS))
			m = Util.extend(m);
		registerAnnotation(m);
		return m;
	}

	protected void edit(MergeEntities op) {
		MutableSetMultimap<Entity, Mention> currentState = Multimaps.mutable.set.empty();
		op.getEntities().forEach(e -> currentState.putAll(e, entityMentionMap.get(e)));
		op.setPreviousState(currentState.toImmutable());
		op.setEntity(merge(op.getEntities()));
		registerEdit(op);
	}

	protected synchronized void edit(CoreferenceModelOperation operation) {
		Annotator.logger.entry(operation);
		if (operation instanceof UpdateEntityName) {
			UpdateEntityName op = (UpdateEntityName) operation;
			op.getEntity().setLabel(op.getNewLabel());
			fireEvent(Event.get(this, Event.Type.Update, op.getEntity()));
		} else if (operation instanceof RemoveDuplicateMentionsInEntities) {
			edit((RemoveDuplicateMentionsInEntities) operation);
		} else if (operation instanceof UpdateEntityKey) {
			UpdateEntityKey op = (UpdateEntityKey) operation;
			if (op.getNewKey() != null && keyMap.containsKey(op.getNewKey())) {
				Entity prev = keyMap.get(op.getNewKey());
				op.setPreviousOwner(prev);
				prev.setKey(null);
			}
			if (op.getNewKey() == null)
				op.getObjects().getFirst().setKey(null);
			else {
				op.getObjects().getFirst().setKey(op.getNewKey().toString());
				keyMap.put(op.getNewKey(), op.getEntity());
			}
			if (op.getPreviousOwner() != null)
				fireEvent(Event.get(this, Event.Type.Update, op.getObjects().getFirst(), op.getPreviousOwner()));
			else
				fireEvent(Event.get(this, Event.Type.Update, op.getObjects().getFirst()));
		} else if (operation instanceof UpdateEntityColor) {
			UpdateEntityColor op = (UpdateEntityColor) operation;
			op.getObjects().getFirst().setColor(op.getNewColor());
			fireEvent(Event.get(this, Event.Type.Update, op.getObjects()));
			fireEvent(Event.get(this, Event.Type.Update, op.getObjects().flatCollect(e -> entityMentionMap.get(e))));
		} else if (operation instanceof AddEntityToEntityGroup) {
			AddEntityToEntityGroup op = (AddEntityToEntityGroup) operation;
			MutableList<Entity> oldArr = Util.toList(op.getEntityGroup().getMembers());

			MutableList<Entity> newMembers = Lists.mutable.withAll(op.getEntities());
			newMembers.removeAll(oldArr);

			op.setEntities(newMembers.toImmutable());

			FSArray arr = new FSArray(jcas, op.getEntityGroup().getMembers().size() + newMembers.size());
			int i = 0;
			for (; i < op.getEntityGroup().getMembers().size(); i++) {
				arr.set(i, op.getEntityGroup().getMembers(i));
			}
			int oldSize = i;
			for (; i < arr.size(); i++) {
				arr.set(i, newMembers.get(i - oldSize));
			}
			arr.addToIndexes();
			op.getEntityGroup().removeFromIndexes();
			op.getEntityGroup().setMembers(arr);
			fireEvent(Event.get(this, Event.Type.Add, op.getEntityGroup(), op.getEntities()));
		} else if (operation instanceof AddMentionsToNewEntity) {
			AddMentionsToNewEntity op = (AddMentionsToNewEntity) operation;
			MutableList<Mention> ms = Lists.mutable.empty();
			for (Span span : op.getSpans()) {
				if (op.getEntity() == null) {
					Mention fst = add(span);
					ms.add(fst);
					op.setEntity(fst.getEntity());
				} else
					ms.add(addTo(op.getEntity(), span));
			}
			fireEvent(Event.get(this, Event.Type.Add, null, op.getEntity()));
			fireEvent(Event.get(this, Event.Type.Add, op.getEntity(), ms.toImmutable()));
		} else if (operation instanceof AddMentionsToEntity) {
			AddMentionsToEntity op = (AddMentionsToEntity) operation;
			op.setMentions(op.getSpans().collect(sp -> {
				return addTo(op.getEntity(), sp);
			}));
			fireEvent(Event.get(this, Event.Type.Add, op.getEntity(), op.getMentions()));
		} else if (operation instanceof AttachPart) {
			AttachPart op = (AttachPart) operation;
			op.setPart(addTo(op.getMention(), op.getSpan()));
			fireEvent(Event.get(this, Event.Type.Add, op.getMention(), op.getPart()));
		} else if (operation instanceof MoveMentionsToEntity) {
			MoveMentionsToEntity op = (MoveMentionsToEntity) operation;
			op.getMentions().forEach(m -> moveTo(op.getTarget(), m));
			fireEvent(Event.get(this, Event.Type.Update, op.getObjects()));
			fireEvent(op.toEvent());
		} else if (operation instanceof MoveMentionPartToMention) {
			MoveMentionPartToMention op = (MoveMentionPartToMention) operation;
			op.getObjects().forEach(d -> {
				d.setMention(op.getTarget());
				op.getTarget().setDiscontinuous(d);
				op.getSource().setDiscontinuous(null);
			});
			fireEvent(op.toEvent());
			fireEvent(Event.get(this, Event.Type.Move, op.getSource(), op.getTarget(), op.getObjects()));
		} else if (operation instanceof RemoveEntities) {
			RemoveEntities op = (RemoveEntities) operation;
			op.getFeatureStructures().forEach(e -> {
				if (entityEntityGroupMap.containsKey(e))
					op.entityEntityGroupMap.putAll(e, entityEntityGroupMap.get(e));
				remove(e);
			});

		} else if (operation instanceof RemoveEntitiesFromEntityGroup) {
			RemoveEntitiesFromEntityGroup op = (RemoveEntitiesFromEntityGroup) operation;
			op.getEntities().forEach(e -> removeFrom(op.getEntityGroup(), e));
		} else if (operation instanceof RemovePart) {
			RemovePart op = (RemovePart) operation;
			remove(op.getPart());
			fireEvent(Event.get(this, Type.Remove, op.getMention(), op.getPart()));
		} else if (operation instanceof GroupEntities) {
			GroupEntities op = (GroupEntities) operation;
			Annotator.logger.trace("Forming entity group with {}.", op.getEntities());
			EntityGroup eg = createEntityGroup(createEntityGroupLabel(op.getEntities()), op.getEntities().size());
			for (int i = 0; i < op.getEntities().size(); i++) {
				eg.setMembers(i, op.getEntities().get(i));
				entityEntityGroupMap.put(op.getEntities().get(i), eg);
			}
			fireEvent(Event.get(this, Event.Type.Add, null, eg));
			op.setEntityGroup(eg);
		} else if (operation instanceof RemoveMention) {
			edit((RemoveMention) operation);
		} else if (operation instanceof RemoveSingletons) {
			edit((RemoveSingletons) operation);
		} else if (operation instanceof MergeEntities) {
			edit((MergeEntities) operation);
		} else if (operation instanceof ToggleGenericFlag) {
			edit((ToggleGenericFlag) operation);
		} else if (operation instanceof RenameAllEntities) {
			edit((RenameAllEntities) operation);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	protected void edit(RemoveDuplicateMentionsInEntities op) {
		MutableSet<Mention> allRemoved = Sets.mutable.empty();

		op.getEntities().forEach(e -> {
			MutableListMultimap<Span, Mention> map = Multimaps.mutable.list.empty();
			MutableList<Mention> toRemove = Lists.mutable.empty();
			for (Mention m : entityMentionMap.get(e)) {
				Span s = new Span(m);
				if (map.containsKey(s)) {
					for (Mention m2 : map.get(s)) {
						if (m2.getDiscontinuous() == null && m.getDiscontinuous() == null) {
							toRemove.add(m);
						} else if (m2.getDiscontinuous() != null && m.getDiscontinuous() != null) {
							Span s1 = new Span(m.getDiscontinuous());
							Span s2 = new Span(m2.getDiscontinuous());
							if (s1.equals(s2)) {
								toRemove.add(m);
							} else {
								map.put(s, m);
							}
						} else {
							map.put(s, m);
						}
					}
				} else {
					map.put(s, m);
				}
			}

			toRemove.forEach(m -> {
				remove(m, false);
				if (m.getDiscontinuous() != null) {
					DetachedMentionPart dmp = m.getDiscontinuous();
					remove(dmp);
					fireEvent(Event.get(this, Type.Remove, m, dmp));
				}
			});
			fireEvent(Event.get(this, Event.Type.Remove, e, toRemove.toImmutable()));
			allRemoved.addAll(toRemove);
		});
		op.setFeatureStructures(allRemoved.toList().toImmutable());
		registerEdit(op);
	}

	protected void edit(RemoveMention op) {
		op.getFeatureStructures().forEach(m -> {
			remove(m, false);
			if (m.getDiscontinuous() != null) {
				DetachedMentionPart dmp = m.getDiscontinuous();
				remove(dmp);
				fireEvent(Event.get(this, Type.Remove, m, dmp));
			}
		});
		fireEvent(Event.get(this, Event.Type.Remove, op.getEntity(), op.getFeatureStructures()));
		registerEdit(op);
	}

	protected void edit(RemoveSingletons operation) {
		MutableSet<Entity> entities = Sets.mutable.empty();
		MutableSet<Mention> mentions = Sets.mutable.empty();
		for (Entity entity : Lists.immutable.withAll(JCasUtil.select(jcas, Entity.class))) {
			ImmutableSet<Mention> ms = getMentions(entity);
			switch (ms.size()) {
			case 0:
				remove(entity);
				entities.add(entity);
				break;
			case 1:
				Mention m = ms.getOnly();
				remove(m.getEntity());
				mentions.add(m);
				break;
			default:
				break;
			}
		}
		operation.setFeatureStructures(entities.toList().toImmutable());
		operation.setMentions(mentions.toList().toImmutable());
		// fireEvent(null); // TODO
		registerEdit(operation);
	}

	protected void edit(RenameAllEntities operation) {
		for (Entity entity : entityMentionMap.keySet()) {

			Mention nameGiver;

			switch (operation.getStrategy()) {
			case LAST:
				nameGiver = entityMentionMap.get(entity).maxBy(m -> m.getBegin());
				break;
			case LONGEST:
				nameGiver = entityMentionMap.get(entity).maxBy(m -> m.getEnd() - m.getBegin());
				break;
			case FIRST:
			default:
				nameGiver = entityMentionMap.get(entity).minBy(m -> m.getBegin());
				break;

			}
			operation.registerOldName(entity, getLabel(entity));
			String newName = nameGiver.getCoveredText();
			entity.setLabel(newName);

		}
		fireEvent(Event.get(this, Event.Type.Update, operation.getOldNames().keySet()));

	}

	protected void edit(ToggleGenericFlag operation) {
		MutableSet<FeatureStructure> featureStructures = Sets.mutable.empty();
		operation.getObjects().forEach(fs -> {
			Feature feature = fs.getType().getFeatureByBaseName("Flags");

			featureStructures.add(fs);

			if (Util.isX(fs, operation.getFlag())) {
				fs.setFeatureValue(feature,
						Util.removeFrom(jcas, (StringArray) fs.getFeatureValue(feature), operation.getFlag()));
			} else {
				fs.setFeatureValue(feature,
						Util.addTo(jcas, (StringArray) fs.getFeatureValue(feature), operation.getFlag()));
			}
		});
		fireEvent(Event.get(this, Event.Type.Update, operation.getObjects()));
		fireEvent(Event.get(this, Event.Type.Update, featureStructures));
		registerEdit(operation);

	}

	protected void fireEvent(FeatureStructureEvent event) {
		crModelListeners.forEach(l -> l.entityEvent(event));
	}

	public ImmutableSet<Mention> get(Entity entity) {
		return entityMentionMap.get(entity).toImmutable();
	}

	@Override
	public DocumentModel getDocumentModel() {
		return documentModel;
	}

	public ImmutableList<Entity> getEntities(final EntitySorter entitySorter) {

		MutableSet<Entity> eset = Sets.mutable.withAll(JCasUtil.select(jcas, Entity.class));
		return eset.toSortedList(new Comparator<Entity>() {

			@Override
			public int compare(Entity o1, Entity o2) {
				switch (entitySorter) {
				case CHILDREN:
					return Integer.compare(entityMentionMap.get(o2).size(), entityMentionMap.get(o1).size());
				case COLOR:
					return Integer.compare(o1.getColor(), o2.getColor());
				default:
					return o1.getLabel().compareTo(o2.getLabel());
				}
			}

		}).toImmutable();
	}

	public JCas getJCas() {
		return documentModel.getJcas();
	}

	public Map<Character, Entity> getKeyMap() {
		return keyMap;
	}

	public String getLabel(Entity entity) {
		if (entity.getLabel() != null)
			return entity.getLabel();

		return get(entity).collect(m -> m.getCoveredText()).maxBy(s -> s.length());
	}

	public ImmutableSortedSet<Mention> getMentions() {
		return SortedSets.immutable.withAll(new AnnotationComparator(), JCasUtil.select(getJCas(), Mention.class));
	}

	public ImmutableSet<Mention> getMentions(Entity entity) {
		return entityMentionMap.get(entity).toImmutable();
	}

	public Mention getNextMention(int position) {
		for (int i = position; i < getDocumentModel().getJcas().getDocumentText().length(); i++) {
			MutableSet<Mention> mentions = characterPosition2AnnotationMap.get(i).selectInstancesOf(Mention.class);
			if (!mentions.isEmpty())
				return mentions.iterator().next();
		}
		return null;
	}

	public Mention getPreviousMention(int position) {
		for (int i = position - 1; i >= 0; i--) {
			MutableSet<Mention> mentions = characterPosition2AnnotationMap.get(i).selectInstancesOf(Mention.class);
			if (!mentions.isEmpty())
				return mentions.iterator().next();
		}
		return null;
	}

	/**
	 * Retrieve all annotations that cover the current character position
	 * 
	 * @param position
	 *            The character position
	 * @return A collection of annotations
	 */
	public MutableSet<Annotation> getMentions(int position) {
		return this.characterPosition2AnnotationMap.get(position);
	}

	public ImmutableSet<Annotation> getMentions(int start, int end) {
		MutableSet<Annotation> mentions = Sets.mutable.empty();
		for (int i = start; i <= end; i++) {
			mentions.addAll(characterPosition2AnnotationMap.get(i).select(a -> a instanceof Mention));
		}
		return mentions.toImmutable();
	}

	public Preferences getPreferences() {
		return documentModel.getPreferences();
	}

	public String getToolTipText(FeatureStructure featureStructure) {
		if (featureStructure instanceof EntityGroup) {
			StringBuilder b = new StringBuilder();
			EntityGroup entityGroup = (EntityGroup) featureStructure;
			if (entityGroup.getMembers().size() > 0) {
				if (entityGroup.getMembers(0) != null && entityGroup.getMembers(0).getLabel() != null)
					b.append(entityGroup.getMembers(0).getLabel());
				for (int i = 1; i < entityGroup.getMembers().size(); i++) {
					b.append(", ");
					b.append(entityGroup.getMembers(i).getLabel());
				}
				return b.toString();
			} else {
				return null;
			}
		} else if (featureStructure instanceof Entity) {
			return ((Entity) featureStructure).getLabel();
		}
		return null;
	}

	@Override
	protected void initializeOnce() {
		for (Entity entity : JCasUtil.select(documentModel.getJcas(), Entity.class)) {
			if (entity.getKey() != null)
				keyMap.put(new Character(entity.getKey().charAt(0)), entity);
		}
		for (Mention mention : JCasUtil.select(documentModel.getJcas(), Mention.class)) {
			entityMentionMap.put(mention.getEntity(), mention);
			mention.getEntity().addToIndexes();
			registerAnnotation(mention);
			if (mention.getDiscontinuous() != null) {
				registerAnnotation(mention.getDiscontinuous());
			}
		}
	}

	@Deprecated
	public void initialPainting() {
		if (initialized)
			return;
		for (Entity entity : JCasUtil.select(jcas, Entity.class)) {
			fireEvent(Event.get(this, Event.Type.Add, null, entity));
			if (entity.getKey() != null)
				keyMap.put(new Character(entity.getKey().charAt(0)), entity);
		}
		for (Mention mention : JCasUtil.select(jcas, Mention.class)) {
			entityMentionMap.put(mention.getEntity(), mention);
			mention.getEntity().addToIndexes();
			registerAnnotation(mention);
			fireEvent(Event.get(this, Event.Type.Add, mention.getEntity(), mention));
			if (mention.getDiscontinuous() != null) {
				registerAnnotation(mention.getDiscontinuous());
				fireEvent(Event.get(this, Event.Type.Add, mention, mention.getDiscontinuous()));
			}
		}
		initialized = true;
	}

	private Entity merge(Iterable<Entity> nodes) {
		Entity biggest = null;
		int size = 0;
		for (Entity n : nodes) {
			if (entityMentionMap.get(n).size() > size) {
				size = entityMentionMap.get(n).size();
				biggest = n;
			}
		}
		final Entity tgt = biggest;
		if (biggest != null)
			for (Entity n : nodes) {
				if (n != tgt) {
					fireEvent(Event.get(this, Event.Type.Move, n, tgt, entityMentionMap.get(n).toList().toImmutable()));
					fireEvent(Event.get(this, Event.Type.Remove, n));
					entityMentionMap.get(n).toSet().forEach(m -> moveTo(tgt, m));

					entityMentionMap.removeAll(n);
					n.removeFromIndexes();
				}
			}
		return biggest;
	}

	/**
	 * does not fire events
	 * 
	 * @param newEntity
	 * @param mentions
	 */
	private void moveTo(Entity newEntity, Mention... mentions) {
		Entity oldEntity = null;
		for (Mention m : mentions) {
			oldEntity = m.getEntity();
			m.setEntity(newEntity);
			entityMentionMap.remove(oldEntity, m);
			entityMentionMap.put(newEntity, m);
		}
	}

	public void registerAnnotation(Annotation a) {
		characterPosition2AnnotationMap.add(a);
	}

	private void registerEdit(Operation operation) {
		documentModel.fireDocumentChangedEvent();
	}

	/**
	 * does not fire evetns
	 * 
	 * @param dmp
	 */
	private void remove(DetachedMentionPart dmp) {
		dmp.removeFromIndexes();
		characterPosition2AnnotationMap.remove(dmp);
	};

	/**
	 * Removes entity and fires events
	 * 
	 * @param entity
	 */
	private void remove(Entity entity) {
		fireEvent(Event.get(this, Event.Type.Remove, entity, entityMentionMap.get(entity).toList().toImmutable()));
		for (Mention m : entityMentionMap.get(entity)) {
			characterPosition2AnnotationMap.remove(m);
			m.removeFromIndexes();
			// TODO: remove parts
		}
		for (EntityGroup group : entityEntityGroupMap.get(entity)) {
			group.setMembers(Util.removeFrom(jcas, group.getMembers(), entity));
		}

		entityEntityGroupMap.removeAll(entity);

		fireEvent(Event.get(this, Event.Type.Remove, null, entity));
		entityMentionMap.removeAll(entity);
		entity.removeFromIndexes();

	}

	private void remove(Mention m, boolean autoRemove) {
		Entity entity = m.getEntity();
		characterPosition2AnnotationMap.remove(m);
		entityMentionMap.remove(entity, m);
		m.removeFromIndexes();
		if (autoRemove && entityMentionMap.get(entity).isEmpty() && getPreferences()
				.getBoolean(Constants.CFG_DELETE_EMPTY_ENTITIES, Defaults.CFG_DELETE_EMPTY_ENTITIES)) {
			remove(entity);
		}

	}

	public boolean removeCoreferenceModelListener(Object o) {
		return crModelListeners.remove(o);
	}

	/**
	 * TODO: this could have a unit test
	 * 
	 * @param eg
	 * @param entity
	 */
	private void removeFrom(EntityGroup eg, Entity entity) {
		FSArray oldArray = eg.getMembers();
		FSArray arr = new FSArray(jcas, eg.getMembers().size() - 1);

		for (int i = 0, j = 0; i < oldArray.size() - 1 && j < arr.size() - 1; i++, j++) {

			if (eg.getMembers(i) == entity) {
				i++;
			}
			arr.set(j, eg.getMembers(i));

		}
		eg.setMembers(arr);
		fireEvent(Event.get(this, Event.Type.Remove, eg, entity));
	}

	protected void undo(CoreferenceModelOperation operation) {
		Annotator.logger.entry(operation);
		if (operation instanceof UpdateEntityName) {
			UpdateEntityName op = (UpdateEntityName) operation;
			op.getEntity().setLabel(op.getOldLabel());
			fireEvent(Event.get(this, Event.Type.Update, op.getEntity()));
		} else if (operation instanceof UpdateEntityKey) {
			UpdateEntityKey op = (UpdateEntityKey) operation;
			if (op.getPreviousOwner() != null) {
				op.getPreviousOwner().setKey(op.getNewKey().toString());
				keyMap.put(op.getNewKey(), op.getPreviousOwner());
			} else {
				keyMap.remove(op.getNewKey());
			}
			if (op.getOldKey() != null) {
				op.getEntity().setKey(op.getOldKey().toString());
				keyMap.put(op.getOldKey(), op.getEntity());
			} else {
				op.getEntity().setKey(null);
			}
			if (op.getPreviousOwner() != null)
				fireEvent(Event.get(this, Event.Type.Update, op.getObjects().getFirst(), op.getPreviousOwner()));
			else
				fireEvent(Event.get(this, Event.Type.Update, op.getObjects().getFirst()));
		} else if (operation instanceof ToggleGenericFlag) {
			edit((ToggleGenericFlag) operation);
		} else if (operation instanceof UpdateEntityColor) {
			UpdateEntityColor op = (UpdateEntityColor) operation;
			op.getObjects().getFirst().setColor(op.getOldColor());
			fireEvent(Event.get(this, Event.Type.Update, op.getObjects()));
			fireEvent(Event.get(this, Event.Type.Update, op.getObjects().flatCollect(e -> entityMentionMap.get(e))));
		} else if (operation instanceof AddEntityToEntityGroup) {
			AddEntityToEntityGroup op = (AddEntityToEntityGroup) operation;
			op.getEntities().forEach(e -> removeFrom(op.getEntityGroup(), e));
		} else if (operation instanceof AddMentionsToNewEntity) {
			AddMentionsToNewEntity op = (AddMentionsToNewEntity) operation;
			remove(op.getEntity());
		} else if (operation instanceof AddMentionsToEntity) {
			AddMentionsToEntity op = (AddMentionsToEntity) operation;
			op.getMentions().forEach(m -> remove(m, false));
			fireEvent(Event.get(this, Event.Type.Remove, op.getEntity(), op.getMentions()));
		} else if (operation instanceof AttachPart) {
			AttachPart op = (AttachPart) operation;
			remove(op.getPart());
			fireEvent(Event.get(this, Event.Type.Remove, op.getMention(), op.getPart()));
		} else if (operation instanceof MoveMentionPartToMention) {
			MoveMentionPartToMention op = (MoveMentionPartToMention) operation;
			op.getObjects().forEach(d -> {
				op.getSource().setDiscontinuous(d);
				d.setMention(op.getSource());
				op.getTarget().setDiscontinuous(null);
			});
			fireEvent(op.toReversedEvent());
		} else if (operation instanceof MoveMentionsToEntity) {
			MoveMentionsToEntity op = (MoveMentionsToEntity) operation;
			op.getMentions().forEach(m -> moveTo(op.getSource(), m));
			fireEvent(Event.get(this, Event.Type.Update, op.getObjects()));
			fireEvent(op.toReversedEvent());
		} else if (operation instanceof RemoveDuplicateMentionsInEntities) {
			RemoveDuplicateMentionsInEntities op = (RemoveDuplicateMentionsInEntities) operation;

			op.getFeatureStructures().forEach(m -> {
				m.addToIndexes();
				entityMentionMap.put(m.getEntity(), m);
				registerAnnotation(m);
				fireEvent(Event.get(this, Type.Add, m.getEntity(), m));
			});
		} else if (operation instanceof RemovePart) {
			RemovePart op = (RemovePart) operation;
			op.getPart().setMention(op.getMention());
			op.getMention().setDiscontinuous(op.getPart());
			fireEvent(Event.get(this, Type.Add, op.getMention(), op.getPart()));
		} else if (operation instanceof RemoveMention) {
			undo((RemoveMention) operation);
		} else if (operation instanceof RemoveEntities) {
			RemoveEntities op = (RemoveEntities) operation;
			op.getFeatureStructures().forEach(e -> {
				e.addToIndexes();
				if (op.entityEntityGroupMap.containsKey(e)) {
					for (EntityGroup group : op.entityEntityGroupMap.get(e))
						group.setMembers(Util.addTo(jcas, group.getMembers(), e));
				}
			});
			fireEvent(Event.get(this, Event.Type.Add, null, op.getFeatureStructures()));
		} else if (operation instanceof RemoveEntitiesFromEntityGroup) {
			RemoveEntitiesFromEntityGroup op = (RemoveEntitiesFromEntityGroup) operation;
			FSArray oldArr = op.getEntityGroup().getMembers();
			FSArray newArr = new FSArray(jcas, oldArr.size() + op.getEntities().size());
			int i = 0;
			for (; i < oldArr.size(); i++) {
				newArr.set(i, oldArr.get(i));
			}
			for (; i < newArr.size(); i++) {
				newArr.set(i, op.getEntities().get(i - oldArr.size()));
			}
			op.getEntityGroup().setMembers(newArr);
			newArr.addToIndexes();
			oldArr.removeFromIndexes();
		} else if (operation instanceof RemoveSingletons) {
			undo((RemoveSingletons) operation);
		} else if (operation instanceof MergeEntities) {
			MergeEntities op = (MergeEntities) operation;
			for (Entity oldEntity : op.getEntities()) {
				if (op.getEntity() != oldEntity) {
					oldEntity.addToIndexes();
					fireEvent(Event.get(this, Event.Type.Add, null, oldEntity));
					for (Mention m : op.getPreviousState().get(oldEntity)) {
						moveTo(oldEntity, m);
					}
					fireEvent(Event.get(this, Type.Move, null, oldEntity,
							op.getPreviousState().get(oldEntity).toList().toImmutable()));
				}
			}
		} else if (operation instanceof GroupEntities) {
			GroupEntities op = (GroupEntities) operation;
			remove(op.getEntityGroup());
			op.getEntities().forEach(e -> entityEntityGroupMap.remove(e, op.getEntityGroup()));
			fireEvent(Event.get(this, Event.Type.Remove, null, op.getEntityGroup()));
		} else if (operation instanceof RenameAllEntities) {
			undo((RenameAllEntities) operation);
		}
	}

	private void undo(RemoveMention op) {
		// re-create all mentions and set them to the op
		op.getFeatureStructures().forEach(m -> {
			m.addToIndexes();
			m.setEntity(op.getEntity());
			entityMentionMap.put(op.getEntity(), m);
			characterPosition2AnnotationMap.add(m);
			if (m.getDiscontinuous() != null) {
				m.getDiscontinuous().addToIndexes();
				characterPosition2AnnotationMap.add(m.getDiscontinuous());
			}
		});
		// fire event to draw them
		fireEvent(Event.get(this, Event.Type.Add, op.getEntity(), op.getFeatureStructures()));
		// re-create attached parts (if any)
		op.getFeatureStructures().select(m -> m.getDiscontinuous() != null)
				.forEach(m -> fireEvent(Event.get(this, Event.Type.Add, m, m.getDiscontinuous())));

	}

	private void undo(RemoveSingletons op) {
		op.getFeatureStructures().forEach(e -> e.addToIndexes());
		op.getMentions().forEach(m -> {
			entityMentionMap.put(m.getEntity(), m);
			characterPosition2AnnotationMap.add(m);
			m.addToIndexes();
			m.getEntity().addToIndexes();
			fireEvent(Event.get(this, Event.Type.Add, null, m.getEntity()));
			fireEvent(Event.get(this, Event.Type.Add, m.getEntity(), m));
		});
		fireEvent(Event.get(this, Event.Type.Add, null, op.getFeatureStructures()));
	}

	protected void undo(RenameAllEntities operation) {
		for (Entity entity : operation.getOldNames().keySet()) {
			entity.setLabel(operation.getOldNames().get(entity));
		}
		fireEvent(Event.get(this, Event.Type.Update, operation.getOldNames().keySet()));
	}

}
