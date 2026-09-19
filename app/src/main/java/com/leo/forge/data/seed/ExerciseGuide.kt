package com.leo.forge.data.seed

import androidx.compose.runtime.Immutable

/**
 * Short form notes for every exercise in the library.
 *
 * Three lines, not an essay: how to set up, how to move, and the one mistake that actually
 * costs people either progress or a joint. Written per movement family, because the cue that
 * matters for a lateral raise is the same whether the load comes from a dumbbell or a cable,
 * and a generic "keep good form" note would be worse than nothing.
 */
@Immutable
data class Cues(val setup: String, val execution: String, val mistake: String)

object ExerciseGuide {

    private val AB_WHEEL = Cues(
        setup = "Start on your knees with the wheel under your shoulders, ribs down, glutes squeezed.",
        execution = "Roll out only as far as you can without the lower back arching, then pull back with the abs.",
        mistake = "Rolling out further than your brace can hold, which loads the lower back.",
    )

    private val ABDUCTION = Cues(
        setup = "Sit with the pads against the outside of your knees, torso upright or slightly leaned forward.",
        execution = "Push the knees apart, hold the end position for a beat, then return slowly.",
        mistake = "Bouncing through the reps with the whole stack.",
    )

    private val ADDUCTOR = Cues(
        setup = "Sit with the pads against the inside of your knees and open to a comfortable stretch, not your maximum.",
        execution = "Squeeze the knees together, hold, then return under control to the stretch.",
        mistake = "Starting from a stretch you cannot control, which strains the groin.",
    )

    private val ARNOLD = Cues(
        setup = "Start with the dumbbells in front of you at chin height, palms facing you, elbows tucked.",
        execution = "Rotate the palms outward as you press up, then reverse the rotation exactly on the way down.",
        mistake = "Rushing the rotation, which is the whole reason for the exercise.",
    )

    private val BACK_EXTENSION = Cues(
        setup = "Pads at the hip crease so the hips can move freely, feet secured.",
        execution = "Hinge down with a neutral spine, then extend back up to a straight line. Do not arch beyond that.",
        mistake = "Hyperextending at the top and cranking the lower back.",
    )

    private val BAND = Cues(
        setup = "Set the band so there is already light tension at the start. Stand tall.",
        execution = "Move through the full range and control the return - the band pulls back hard, which is the useful part.",
        mistake = "Letting the band snap back and skipping the negative.",
    )

    private val BB_BENCH = Cues(
        setup = "Eyes under the bar. Pull your shoulder blades back and down into the bench and keep them there. Feet flat, driving into the floor. Grip a little wider than shoulder width.",
        execution = "Lower under control to the lower chest with elbows roughly 45-75 degrees from your torso. Touch, then press back up and slightly back towards your face.",
        mistake = "Flaring the elbows straight out to 90 degrees. It punishes the shoulder and shortens your leverage.",
    )

    private val BULGARIAN = Cues(
        setup = "Back foot on the bench, front foot far enough forward that the shin stays near vertical. Torso slightly forward.",
        execution = "Lower until the back knee is close to the floor and you feel the front glute stretch, then drive up through the front foot.",
        mistake = "Standing too close to the bench, which turns it into a knee-grinding half rep.",
    )

    private val CABLE_CRUNCH = Cues(
        setup = "Kneel facing the stack with the rope beside your head, hips fixed so they do not move all set.",
        execution = "Crunch by rounding the spine and bringing the ribs towards the pelvis, squeeze, then uncurl slowly.",
        mistake = "Hinging at the hips, which turns it into an odd pulldown.",
    )

    private val CABLE_PRESS = Cues(
        setup = "Set the pulleys at chest height, split stance for balance, a step forward so there is tension at the start.",
        execution = "Press forward and together, squeezing at the end, then let the hands travel back until the chest stretches.",
        mistake = "Standing too close to the stack, so tension disappears at the finish.",
    )

    private val CALF = Cues(
        setup = "Balls of the feet on the platform, heels hanging free, knees straight for the gastroc or bent for the soleus.",
        execution = "Drop the heels for a full stretch, pause, then press all the way up onto the toes and hold at the top.",
        mistake = "Bouncing out of the bottom on the Achilles rather than pressing with the calf.",
    )

    private val CARRY = Cues(
        setup = "Pick the weight up with a flat back, stand tall, shoulders down, ribs stacked over hips.",
        execution = "Walk in a straight line with short controlled steps, breathing steadily. Set it down before your grip gives out entirely.",
        mistake = "Leaning back and letting the ribs flare. Stay stacked - the carry trains your brace as much as your grip.",
    )

    private val CLOSE_GRIP = Cues(
        setup = "Grip inside shoulder width but not so narrow the wrists complain. Elbows tucked close to your sides.",
        execution = "Lower to the lower chest keeping the elbows in, then press up and think about extending the elbows rather than pushing.",
        mistake = "Gripping so narrowly that the wrists take the punishment.",
    )

    private val CURL_BB = Cues(
        setup = "Stand tall, elbows at your sides, shoulder-width grip, ribs down.",
        execution = "Curl by bending the elbow only, squeeze at the top, then lower slowly to a full stretch.",
        mistake = "Swinging the bar up with the hips and calling it a curl.",
    )

    private val CURL_CABLE = Cues(
        setup = "Step back so there is tension at the bottom, elbows at your sides.",
        execution = "Curl with the elbow only, squeeze, and resist the weight all the way back down.",
        mistake = "Standing too close so the cable goes slack at the bottom.",
    )

    private val CURL_DB = Cues(
        setup = "Stand or sit tall, arms hanging, palms forward, elbows pinned.",
        execution = "Curl up, supinating so the palm faces up at the top, squeeze, then lower fully under control.",
        mistake = "Cutting the bottom of the rep short, which is where the biceps is loaded hardest.",
    )

    private val CURL_INCLINE = Cues(
        setup = "Set the bench to about 45-60 degrees and let the arms hang straight down behind your torso.",
        execution = "Curl without letting the upper arm drift forward, squeeze, then return to a full stretch.",
        mistake = "Letting the shoulder swing forward, which removes the stretch this variation exists for.",
    )

    private val DB_BENCH = Cues(
        setup = "Sit with the dumbbells on your thighs and kick them up as you lie back. Shoulder blades pinned, wrists stacked over your elbows.",
        execution = "Lower until your upper arms are about level with your torso and you feel the chest stretch. Press up and slightly together without banging them.",
        mistake = "Chasing depth until the shoulder rolls forward. Stop where the chest stretches, not where the joint gives.",
    )

    private val DEADLIFT = Cues(
        setup = "Bar over the middle of your foot, shins close, flat back, lats engaged and shoulders just in front of the bar.",
        execution = "Push the floor away and drag the bar up your legs. Lock out by squeezing the glutes, not by leaning back.",
        mistake = "Letting the hips rise first so the bar drifts away and the lower back takes it.",
    )

    private val DIP = Cues(
        setup = "Grip the bars, arms locked, shoulders pulled down away from your ears. Lean the torso forward for chest, stay upright for triceps.",
        execution = "Lower until your upper arms are about parallel to the floor, then press back up without shrugging.",
        mistake = "Dropping until the shoulders roll forward at the bottom. Depth is not worth a shoulder.",
    )

    private val FACE_PULL = Cues(
        setup = "Rope at roughly head height, step back so there is tension, thumbs pointing back.",
        execution = "Pull the rope towards your forehead, splitting your hands apart and rotating the knuckles up. Squeeze the rear delts.",
        mistake = "Pulling to the chin with the elbows low, which makes it a row.",
    )

    private val FLOOR_PRESS = Cues(
        setup = "Lie on the floor with knees bent, dumbbells over your chest. The floor limits your range, which is exactly the point when there is no rack.",
        execution = "Lower until your triceps touch the floor, pause for a beat, then press.",
        mistake = "Bouncing the elbows off the floor to get out of the bottom.",
    )

    private val FLY_CABLE = Cues(
        setup = "Pulleys set to match the angle you want, split stance, a small bend in the elbow that never changes.",
        execution = "Sweep the hands together in a hugging arc, squeeze, then open back out until you feel a stretch across the chest.",
        mistake = "Bending and straightening the elbows, which makes it a press instead of a fly.",
    )

    private val FLY_DB = Cues(
        setup = "Lie back with a soft, fixed elbow bend and the dumbbells over your chest, shoulder blades pinned.",
        execution = "Open the arms out wide until the chest stretches, then bring them back over the chest along the same arc.",
        mistake = "Going too heavy and pressing the weight instead of flying it - you will feel it in the shoulder, not the chest.",
    )

    private val FRONT_RAISE = Cues(
        setup = "Stand tall, weights in front of your thighs, a slight bend in the elbows, ribs down.",
        execution = "Raise to about shoulder height under control, then lower slowly. No higher is needed.",
        mistake = "Swinging from the hips. If you need momentum the weight is wrong.",
    )

    private val GLUTE_BRIDGE = Cues(
        setup = "Lie on your back with the feet planted close enough that the shins come vertical at the top.",
        execution = "Push through the heels and squeeze the glutes until the hips are locked out, hold, then lower.",
        mistake = "Arching the lower back instead of finishing with the glutes.",
    )

    private val GLUTE_KICKBACK = Cues(
        setup = "Hinge slightly forward with a firm brace, working leg bent or straight depending on the attachment.",
        execution = "Drive the leg back using the glute, squeeze at the end, then return under control.",
        mistake = "Arching the lower back to get more range than the hip actually has.",
    )

    private val GOBLET = Cues(
        setup = "Hold the weight at your chest like a goblet, elbows tucked inside your knees, chest up.",
        execution = "Squat down between your knees keeping the torso upright, then stand up through the whole foot.",
        mistake = "Letting the weight pull you forward into a rounded back.",
    )

    private val GOOD_MORNING = Cues(
        setup = "Bar on the upper back, soft knees, a hard brace. Start much lighter than you think.",
        execution = "Hinge at the hips with a flat back until you feel the hamstrings, then drive the hips forward to stand.",
        mistake = "Rounding the back under load, which is the fastest way to hurt yourself on this one.",
    )

    private val HAMMER = Cues(
        setup = "Stand tall, palms facing each other, elbows at your sides.",
        execution = "Curl up keeping the palms facing in the whole way, then lower slowly.",
        mistake = "Letting the wrist roll over, which turns it into a regular curl.",
    )

    private val HIP_THRUST = Cues(
        setup = "Shoulder blades on the bench, feet planted so the shins are vertical at the top. Tuck the ribs down.",
        execution = "Drive through the heels until the hips are level with the knees, squeeze hard at the top, then lower under control.",
        mistake = "Overextending the lower back at the top instead of squeezing the glutes.",
    )

    private val INCLINE = Cues(
        setup = "Set the bench to 30-45 degrees. Any steeper and it quietly becomes a shoulder press. Shoulder blades back and down.",
        execution = "Lower to the upper chest just below the collarbone, then press up without letting the elbows drift behind you.",
        mistake = "Setting the bench too upright, which hands the work to the front delts.",
    )

    private val INVERTED_ROW = Cues(
        setup = "Bar set around hip height, body straight from heels to head, heels on the floor.",
        execution = "Pull your chest to the bar leading with the elbows, hold for a beat, then lower under control.",
        mistake = "Letting the hips sag so it becomes an awkward half-plank.",
    )

    private val KB_SWING = Cues(
        setup = "Feet about shoulder width, bell a foot in front, hinge and hike it back between your legs like a rugby pass.",
        execution = "Snap the hips forward to float the bell to chest height - the arms just hold on. Let it fall and hinge again.",
        mistake = "Lifting the bell with the shoulders instead of throwing it with the hips.",
    )

    private val KICKBACK = Cues(
        setup = "Hinge forward with the upper arm parallel to the floor and pinned there.",
        execution = "Extend the elbow until the arm is straight, hold for a beat, then control back.",
        mistake = "Swinging the whole arm instead of moving only at the elbow.",
    )

    private val LANDMINE_PRESS = Cues(
        setup = "Face the bar with the end at shoulder height, feet staggered, a firm brace through the midsection.",
        execution = "Press the bar up and slightly across your midline, then control it back to the shoulder.",
        mistake = "Twisting the torso to get the weight up instead of pressing it.",
    )

    private val LANDMINE_ROW = Cues(
        setup = "Straddle the bar, hinge to about 45 degrees, flat back, both hands on the handle or the sleeve.",
        execution = "Pull the end of the bar to your midsection with the elbows close, squeeze, then lower into a stretch.",
        mistake = "Standing up as you pull, so the hips finish the rep instead of the back. Fix the torso angle and move only the arms.",
    )

    private val LATERAL_RAISE = Cues(
        setup = "Stand tall with a small forward lean, weights at your sides, elbows softly bent and fixed.",
        execution = "Lead with the elbows and raise out to the side to roughly shoulder height, little fingers slightly high. Lower slowly - the negative is most of the work.",
        mistake = "Shrugging the weight up with the traps. Keep the shoulders down and let the side delt do it.",
    )

    private val LEG_CURL = Cues(
        setup = "Pad just above the heels, knee lined up with the machine's pivot, hips flat against the seat.",
        execution = "Curl until the hamstrings are fully shortened, squeeze, then lower slowly to a full stretch.",
        mistake = "Lifting the hips off the pad to get more range.",
    )

    private val LEG_EXTENSION = Cues(
        setup = "Seat and pad set so the knee lines up with the machine's pivot and the pad sits low on the shin.",
        execution = "Extend until the legs are straight, squeeze for a beat, then lower slowly.",
        mistake = "Slamming into lockout and letting the stack drop on the way back.",
    )

    private val LEG_PRESS = Cues(
        setup = "Feet about shoulder width on the platform, back and hips flat against the seat.",
        execution = "Lower until the knees approach your chest without the hips curling off the pad, then press back without locking the knees hard.",
        mistake = "Chasing depth until the pelvis tucks - the classic way to hurt your lower back on a leg press.",
    )

    private val LEG_RAISE = Cues(
        setup = "Hang or lie with the shoulders down and the lower back not overarched.",
        execution = "Lift the knees or legs by curling the pelvis towards the ribs, not just by flexing the hip. Lower slowly.",
        mistake = "Swinging. If you are moving back and forth, the abs are not doing it.",
    )

    private val LUNGE = Cues(
        setup = "Stand tall with the weight at your sides. Take a long enough step that the front shin stays fairly vertical.",
        execution = "Lower until the back knee is just off the floor, then drive up through the front heel.",
        mistake = "Too short a step, which puts the whole set into the front knee.",
    )

    private val MACHINE_CRUNCH = Cues(
        setup = "Seat and pad set so the pivot lines up with your midsection, hands on the handles.",
        execution = "Crunch by shortening the abs, not by pulling with the arms. Return slowly.",
        mistake = "Yanking with the arms and letting the abs go along for the ride.",
    )

    private val MACHINE_PRESS_CHEST = Cues(
        setup = "Set the seat so the handles sit at mid-chest. Back flat against the pad, feet planted.",
        execution = "Press out and slightly together, stopping just short of locking out. Control the way back until you feel the chest lengthen.",
        mistake = "A seat set too high, which turns it into an awkward incline and pinches the shoulder.",
    )

    private val MACHINE_SHOULDER_PRESS = Cues(
        setup = "Seat set so the handles are at about shoulder height. Back on the pad, feet planted.",
        execution = "Press up smoothly to just short of lockout, then lower until the elbows are level with the shoulders.",
        mistake = "Setting the seat too low, which forces the shoulder into an awkward start.",
    )

    private val NORDIC = Cues(
        setup = "Kneel with the ankles anchored, hips extended, body in a straight line from knee to shoulder.",
        execution = "Lower yourself as slowly as you can, resisting the whole way, then push off or pull yourself back up.",
        mistake = "Breaking at the hips so it becomes a controlled fall rather than a hamstring exercise.",
    )

    private val OHP_BB = Cues(
        setup = "Bar on the front delts, grip just outside shoulder width, elbows slightly in front. Squeeze the glutes and brace so you do not lean back.",
        execution = "Press up, moving your head back out of the way, then finish with the bar over the middle of your foot and the elbows locked.",
        mistake = "Leaning back through the lower back to get the bar up. That is a standing incline press.",
    )

    private val OHP_DB = Cues(
        setup = "Sit or stand tall with the dumbbells at shoulder height, palms forward, ribs down.",
        execution = "Press up until the arms are straight without clashing the bells, then lower until the elbows are level with your shoulders.",
        mistake = "Arching the lower back to finish the rep. If you cannot press it without leaning away, the weight is picking the range.",
    )

    private val OVERHEAD_TRICEPS = Cues(
        setup = "Get the weight overhead with the elbows pointing up and close together, ribs down.",
        execution = "Lower behind your head until you feel a strong stretch in the long head, then extend without letting the elbows flare.",
        mistake = "Letting the elbows splay wide, which loses the stretch that makes this the best triceps builder.",
    )

    private val PEC_DECK = Cues(
        setup = "Seat set so the handles are at chest height and your elbows sit slightly below shoulder level. Back flat on the pad.",
        execution = "Bring the arms together under control, squeeze for a beat, then let them open until the chest lengthens.",
        mistake = "Slamming the pads together and letting the stack drop on the way back.",
    )

    private val PIKE_PUSHUP = Cues(
        setup = "Hands shoulder width, hips piked high so your torso is close to vertical. The higher the hips, the more this is a shoulder press.",
        execution = "Lower the crown of your head towards the floor between your hands, then press back up.",
        mistake = "Letting the hips drop mid-rep, which turns it back into a regular push-up.",
    )

    private val PLANK = Cues(
        setup = "Elbows under the shoulders, body in one line, glutes and abs squeezed hard.",
        execution = "Hold, breathing normally, ribs pulled down and hips level. Stop when the form breaks, not at an arbitrary time.",
        mistake = "Letting the hips sag or rise. Either way the abs stop working.",
    )

    private val PREACHER = Cues(
        setup = "Armpits over the top of the pad, upper arms flat against it, a full but not locked stretch at the bottom.",
        execution = "Curl up without lifting the arms off the pad, then lower slowly to just short of full lockout.",
        mistake = "Dropping the weight at the bottom, where the joint is most exposed.",
    )

    private val PULL_THROUGH = Cues(
        setup = "Face away from a low pulley, rope between your legs, soft knees, a step out for tension.",
        execution = "Hinge back letting the rope travel between your legs until the hamstrings stretch, then snap the hips forward and squeeze.",
        mistake = "Squatting up and down instead of hinging. The knees stay soft and still; all the movement comes from the hips.",
    )

    private val PULLDOWN = Cues(
        setup = "Thighs locked under the pad, chest up, a slight lean back that you then hold still.",
        execution = "Pull the bar to your upper chest by driving the elbows down and back. Control the bar all the way up until you feel the lats stretch.",
        mistake = "Rocking backwards to move the weight. If the torso swings, drop the pin.",
    )

    private val PULLOVER = Cues(
        setup = "Arms nearly straight with a fixed slight elbow bend, overhead or out in front depending on the version.",
        execution = "Pull the arms down in an arc to your thighs using the lats, then let them travel back up into a deep stretch.",
        mistake = "Bending the elbows, which hands the work to the triceps.",
    )

    private val PULLUP = Cues(
        setup = "Hang at full stretch with the shoulders pulled down out of your ears. Grip just outside shoulder width.",
        execution = "Pull the elbows down and towards your ribs until your chin clears the bar, chest leading. Lower all the way to a straight-arm hang.",
        mistake = "Half reps from a dead hang you never fully reach. Range is where the growth is.",
    )

    private val PUSHDOWN = Cues(
        setup = "Elbows pinned to your sides, a slight forward lean, wrists neutral.",
        execution = "Extend the elbows until the arms are straight, squeeze, then let the forearms rise only until the elbows want to move.",
        mistake = "Letting the elbows drift forward and back, which turns it into a press.",
    )

    private val PUSHUP = Cues(
        setup = "Hands a little wider than shoulders, body in one line from head to heels, ribs down and glutes tight.",
        execution = "Lower with elbows at roughly 45 degrees until the chest is just off the floor, then push the floor away.",
        mistake = "Letting the hips sag, which turns it into a lower-back exercise.",
    )

    private val RDL = Cues(
        setup = "Stand tall with the weight at your thighs, knees softly bent and then kept there. Shoulders back.",
        execution = "Push the hips back and let the weight travel down your legs until you feel a strong hamstring stretch. Stand up by driving the hips forward.",
        mistake = "Squatting it down instead of hinging. The knees should barely move.",
    )

    private val REAR_DELT = Cues(
        setup = "Hinge at the hips or set the pad so your chest is supported. Soft, fixed elbows.",
        execution = "Open the arms out and slightly back, leading with the elbows, and squeeze behind you. Control the return.",
        mistake = "Turning it into a row by pulling the elbows down and back instead of out.",
    )

    private val REVERSE_NORDIC = Cues(
        setup = "Kneel tall with the body straight from knee to shoulder, glutes squeezed.",
        execution = "Lean back slowly, keeping the hips extended, until the quads scream. Return under control.",
        mistake = "Sitting back onto your heels, which removes the stretch entirely.",
    )

    private val ROW_BB = Cues(
        setup = "Hinge to roughly 45 degrees or lower, bar hanging under the shoulders, back flat and braced.",
        execution = "Pull the bar to the lower ribs or navel by driving the elbows back. Squeeze, then lower under control.",
        mistake = "Standing up a little on every rep. Fix the torso angle and let the arms do the travelling.",
    )

    private val ROW_CABLE = Cues(
        setup = "Chest up, a slight forward lean at the start to get a stretch, knees soft.",
        execution = "Pull the handle to your midsection with the elbows close, squeeze the shoulder blades, then let the arms extend fully.",
        mistake = "Heaving with the lower back on every rep. The torso should stay put while the arms and shoulder blades do the travelling.",
    )

    private val ROW_DB = Cues(
        setup = "Brace a hand and knee on the bench, or hinge with the chest supported. Back flat.",
        execution = "Pull the dumbbell to your hip by driving the elbow back, squeeze the lat, then lower into a full stretch.",
        mistake = "Rotating the torso to throw the weight up instead of rowing it.",
    )

    private val ROW_MACHINE = Cues(
        setup = "Chest against the pad, seat height set so the handles are at mid-torso.",
        execution = "Drive the elbows back until the handles reach your torso, squeeze, then let the arms straighten fully.",
        mistake = "Pulling the chest off the pad to get extra range that is not really there.",
    )

    private val SHRUG = Cues(
        setup = "Stand tall with the weight hanging at arm's length, arms straight and relaxed. Do not lean back.",
        execution = "Shrug the shoulders straight up towards your ears, pause at the top, then lower under control for a full stretch.",
        mistake = "Rolling the shoulders. It adds nothing and irritates the joint.",
    )

    private val SISSY = Cues(
        setup = "Stand tall, hold something for balance, rise onto the balls of your feet.",
        execution = "Let the knees travel forward and the hips extend as you lower back, then pull yourself up with the quads.",
        mistake = "Bending at the hips, which makes it a squat and defeats the purpose.",
    )

    private val SITUP = Cues(
        setup = "Feet anchored, hands across your chest or behind your head without pulling on the neck.",
        execution = "Curl up one vertebra at a time, then lower with the same control.",
        mistake = "Hauling on your own head and firing up from the hip flexors.",
    )

    private val SKULLCRUSHER = Cues(
        setup = "Lie back with the bar over your forehead, not over your chest. Elbows pointing up.",
        execution = "Bend at the elbow to lower the bar just past the top of your head, then extend back without moving the upper arm.",
        mistake = "Turning it into a close-grip press as soon as it gets hard.",
    )

    private val SMITH = Cues(
        setup = "Set the bar height so you can unrack without straining, and position yourself for the fixed bar path.",
        execution = "Move through the range the machine dictates, staying smooth at both ends.",
        mistake = "Fighting the fixed path by drifting out of position mid-set.",
    )

    private val SQUAT_BB = Cues(
        setup = "Bar on the upper back, feet about shoulder width with the toes turned slightly out. Brace as if about to take a punch.",
        execution = "Sit down and slightly back, knees tracking over the toes, until the hip crease is at or below the knee. Drive up through the whole foot.",
        mistake = "Letting the hips shoot up first out of the bottom, which turns it into a good morning.",
    )

    private val SQUAT_FRONT = Cues(
        setup = "Bar across the front delts with the elbows held high. Feet about shoulder width.",
        execution = "Descend with an upright torso, elbows up the whole way, then drive up without letting the chest fold.",
        mistake = "Dropping the elbows, which sends the bar forward and ends the set.",
    )

    private val SQUAT_MACHINE = Cues(
        setup = "Feet on the platform about shoulder width, back and hips flat against the pads.",
        execution = "Lower under control to the depth your hips allow without the lower back rounding, then press through the whole foot.",
        mistake = "Letting the lower back round off the pad at the bottom.",
    )

    private val STEP_UP = Cues(
        setup = "Box or bench at about knee height. Place the whole foot on it.",
        execution = "Drive through the top foot to stand up, keeping the trailing leg passive, then lower under control.",
        mistake = "Pushing off the floor with the back leg to get up.",
    )

    private val TRICEPS_MACHINE = Cues(
        setup = "Set the seat so your elbows line up with the machine's pivot and sit against the pad.",
        execution = "Extend fully, squeeze, then return only until the elbows want to lift off the pad.",
        mistake = "Letting the elbows come off the pad at the top of the return.",
    )

    private val UPRIGHT_ROW = Cues(
        setup = "Grip at about shoulder width - narrower than that crowds the shoulder. Stand tall.",
        execution = "Pull the elbows up and out to the side, stopping when the upper arms reach about shoulder height.",
        mistake = "Pulling too high with a narrow grip, which is a classic route to an impinged shoulder.",
    )

    private val WOODCHOP = Cues(
        setup = "Split stance side-on to the pulley, arms fairly straight, core braced.",
        execution = "Rotate through the torso, not the arms, and follow through across the body. Control it back.",
        mistake = "Pulling with the arms and leaving the trunk out of it.",
    )

    private val WRIST = Cues(
        setup = "Forearms supported on a bench or your thighs, wrists just past the edge.",
        execution = "Move only at the wrist through the biggest range you can manage, and pause at both ends.",
        mistake = "Using the elbow. The forearm should not move at all.",
    )

    private val byExercise: Map<String, Cues> = mapOf(
        "ab_wheel_rollout" to AB_WHEEL,
        "hip_abduction_machine" to ABDUCTION,
        "adductor_machine" to ADDUCTOR,
        "arnold_press" to ARNOLD,
        "back_extension" to BACK_EXTENSION,
        "reverse_hyperextension" to BACK_EXTENSION,
        "band_face_pull" to BAND,
        "band_lateral_raise" to BAND,
        "band_pull_apart" to BAND,
        "barbell_bench_press" to BB_BENCH,
        "decline_barbell_bench_press" to BB_BENCH,
        "bulgarian_split_squat" to BULGARIAN,
        "cable_crunch" to CABLE_CRUNCH,
        "cable_chest_press" to CABLE_PRESS,
        "leg_press_calf_raise" to CALF,
        "seated_calf_raise" to CALF,
        "smith_calf_raise" to CALF,
        "standing_calf_raise" to CALF,
        "farmer_s_carry" to CARRY,
        "close_grip_bench_press" to CLOSE_GRIP,
        "jm_press" to CLOSE_GRIP,
        "barbell_curl" to CURL_BB,
        "ez_bar_curl" to CURL_BB,
        "reverse_curl" to CURL_BB,
        "bayesian_cable_curl" to CURL_CABLE,
        "cable_curl" to CURL_CABLE,
        "concentration_curl" to CURL_DB,
        "dumbbell_curl" to CURL_DB,
        "incline_dumbbell_curl" to CURL_INCLINE,
        "spider_curl" to CURL_INCLINE,
        "dumbbell_bench_press" to DB_BENCH,
        "conventional_deadlift" to DEADLIFT,
        "sumo_deadlift" to DEADLIFT,
        "triceps_dip" to DIP,
        "weighted_dip" to DIP,
        "face_pull" to FACE_PULL,
        "dumbbell_floor_press" to FLOOR_PRESS,
        "cable_fly" to FLY_CABLE,
        "high_to_low_cable_fly" to FLY_CABLE,
        "low_to_high_cable_fly" to FLY_CABLE,
        "dumbbell_fly" to FLY_DB,
        "incline_dumbbell_fly" to FLY_DB,
        "cable_front_raise" to FRONT_RAISE,
        "dumbbell_front_raise" to FRONT_RAISE,
        "barbell_glute_bridge" to GLUTE_BRIDGE,
        "bodyweight_glute_bridge" to GLUTE_BRIDGE,
        "cable_glute_kickback" to GLUTE_KICKBACK,
        "goblet_squat" to GOBLET,
        "kettlebell_goblet_squat" to GOBLET,
        "good_morning" to GOOD_MORNING,
        "cable_hammer_curl" to HAMMER,
        "hammer_curl" to HAMMER,
        "dumbbell_hip_thrust" to HIP_THRUST,
        "hip_thrust" to HIP_THRUST,
        "machine_hip_thrust" to HIP_THRUST,
        "incline_barbell_bench_press" to INCLINE,
        "incline_dumbbell_press" to INCLINE,
        "incline_machine_press" to INCLINE,
        "smith_incline_press" to INCLINE,
        "inverted_row" to INVERTED_ROW,
        "kettlebell_swing" to KB_SWING,
        "cable_kickback" to KICKBACK,
        "landmine_press" to LANDMINE_PRESS,
        "landmine_row" to LANDMINE_ROW,
        "meadows_row" to LANDMINE_ROW,
        "t_bar_row" to LANDMINE_ROW,
        "cable_lateral_raise" to LATERAL_RAISE,
        "dumbbell_lateral_raise" to LATERAL_RAISE,
        "lean_away_lateral_raise" to LATERAL_RAISE,
        "machine_lateral_raise" to LATERAL_RAISE,
        "lying_leg_curl" to LEG_CURL,
        "seated_leg_curl" to LEG_CURL,
        "single_leg_leg_curl" to LEG_CURL,
        "leg_extension" to LEG_EXTENSION,
        "leg_press" to LEG_PRESS,
        "hanging_knee_raise" to LEG_RAISE,
        "hanging_leg_raise" to LEG_RAISE,
        "walking_lunge" to LUNGE,
        "machine_crunch" to MACHINE_CRUNCH,
        "machine_chest_press" to MACHINE_PRESS_CHEST,
        "machine_shoulder_press" to MACHINE_SHOULDER_PRESS,
        "nordic_curl" to NORDIC,
        "overhead_press" to OHP_BB,
        "cable_overhead_press" to OHP_DB,
        "seated_dumbbell_shoulder_press" to OHP_DB,
        "smith_overhead_press" to OHP_DB,
        "dumbbell_overhead_extension" to OVERHEAD_TRICEPS,
        "overhead_cable_extension" to OVERHEAD_TRICEPS,
        "pec_deck" to PEC_DECK,
        "reverse_pec_deck" to PEC_DECK,
        "pike_push_up" to PIKE_PUSHUP,
        "plank" to PLANK,
        "machine_preacher_curl" to PREACHER,
        "preacher_curl" to PREACHER,
        "cable_pull_through" to PULL_THROUGH,
        "kneeling_cable_pulldown" to PULLDOWN,
        "lat_pulldown" to PULLDOWN,
        "machine_pulldown" to PULLDOWN,
        "neutral_grip_pulldown" to PULLDOWN,
        "single_arm_lat_pulldown" to PULLDOWN,
        "cable_pullover" to PULLOVER,
        "dumbbell_pullover" to PULLOVER,
        "machine_pullover" to PULLOVER,
        "straight_arm_pulldown" to PULLOVER,
        "chin_up" to PULLUP,
        "pull_up" to PULLUP,
        "rope_pushdown" to PUSHDOWN,
        "triceps_pushdown" to PUSHDOWN,
        "push_up" to PUSHUP,
        "dumbbell_romanian_deadlift" to RDL,
        "romanian_deadlift" to RDL,
        "single_leg_romanian_deadlift" to RDL,
        "stiff_leg_deadlift" to RDL,
        "bent_over_reverse_fly" to REAR_DELT,
        "cable_reverse_fly" to REAR_DELT,
        "cable_y_raise" to REAR_DELT,
        "dumbbell_rear_delt_fly" to REAR_DELT,
        "reverse_nordic_curl" to REVERSE_NORDIC,
        "barbell_row" to ROW_BB,
        "pendlay_row" to ROW_BB,
        "seated_cable_row" to ROW_CABLE,
        "standing_cable_row" to ROW_CABLE,
        "chest_supported_row" to ROW_DB,
        "dumbbell_row" to ROW_DB,
        "machine_row" to ROW_MACHINE,
        "barbell_shrug" to SHRUG,
        "cable_shrug" to SHRUG,
        "chest_supported_dumbbell_shrug" to SHRUG,
        "dumbbell_shrug" to SHRUG,
        "trap_bar_shrug" to SHRUG,
        "sissy_squat" to SISSY,
        "decline_sit_up" to SITUP,
        "skull_crusher" to SKULLCRUSHER,
        "smith_machine_bench_press" to SMITH,
        "back_squat" to SQUAT_BB,
        "front_squat" to SQUAT_FRONT,
        "hack_squat" to SQUAT_MACHINE,
        "pendulum_squat" to SQUAT_MACHINE,
        "smith_squat" to SQUAT_MACHINE,
        "step_up" to STEP_UP,
        "machine_triceps_extension" to TRICEPS_MACHINE,
        "cable_upright_row" to UPRIGHT_ROW,
        "dumbbell_upright_row" to UPRIGHT_ROW,
        "upright_row" to UPRIGHT_ROW,
        "cable_woodchop" to WOODCHOP,
        "reverse_wrist_curl" to WRIST,
        "wrist_curl" to WRIST,
    )

    /** Null when an exercise has no notes - a custom or imported one, typically. */
    fun forExercise(exerciseId: String): Cues? = byExercise[exerciseId]

    val coveredCount: Int get() = byExercise.size
}
