package com.android.systemui.scene.ui.composable

import com.android.compose.animation.scene.DefaultInterruptionHandler
import com.android.compose.animation.scene.SceneTransitions
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.reveal.ContainerRevealHaptics
import com.android.compose.animation.scene.transitions
import com.android.internal.jank.Cuj
import com.android.mechanics.behavior.VerticalExpandContainerSpec
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.SlightlyFasterShadeCollapse
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.scene.ui.composable.transitions.bouncerToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.bouncerToLockscreenPreview
import com.android.systemui.scene.ui.composable.transitions.communalToBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.communalToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToCommunalTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.dreamToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.goneToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.goneToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.goneToSplitShadeTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToCommunalTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToDreamTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToSplitShadeTransition
import com.android.systemui.scene.ui.composable.transitions.shadeToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.toBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.toNotificationsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.toQuickSettingsShadeTransition
import com.android.systemui.shade.ui.composable.Shade

/**
 * Comprehensive definition of all transitions between scenes and overlays in [SceneContainer].
 *
 * Transitions are automatically reversible, so define only one transition per scene pair. By\
 * convention, use the more common transition direction when defining the pair order, e.g.
 * Lockscreen to Bouncer rather than Bouncer to Lockscreen.
 *
 * The actual transition DSL must be placed in a separate file under the package
 * [com.android.systemui.scene.ui.composable.transitions].
 *
 * Please keep the list sorted alphabetically.
 */
class SceneContainerTransitions : SceneContainerTransitionsBuilder {
    override fun build(
        shadeExpansionMotion: VerticalExpandContainerSpec,
        revealHaptics: ContainerRevealHaptics,
    ): SceneTransitions {
        return transitions {
            interruptionHandler = DefaultInterruptionHandler

            // Scene transitions

            from(Scenes.Dream, to = Scenes.Communal) { dreamToCommunalTransition() }
            from(Scenes.Dream, to = Scenes.Gone) { dreamToGoneTransition() }
            from(
                Scenes.Dream,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                dreamToShadeTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                goneToShadeTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.Shade,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                goneToSplitShadeTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.Shade,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                goneToShadeTransition(durationScale = 0.9)
            }
            from(
                Scenes.Gone,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                goneToQuickSettingsTransition()
            }
            from(
                Scenes.Gone,
                to = Scenes.QuickSettings,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                goneToQuickSettingsTransition(durationScale = 0.9)
            }

            from(Scenes.Lockscreen, to = Scenes.Communal) { lockscreenToCommunalTransition() }
            from(Scenes.Lockscreen, to = Scenes.Dream) { lockscreenToDreamTransition() }
            from(
                Scenes.Lockscreen,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                lockscreenToShadeTransition()
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.Shade,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                lockscreenToSplitShadeTransition()
                sharedElement(Shade.Elements.BackgroundScrim, enabled = false)
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.Shade,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                lockscreenToShadeTransition(durationScale = 0.9)
            }
            from(
                Scenes.Lockscreen,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                lockscreenToQuickSettingsTransition()
            }
            from(Scenes.Lockscreen, to = Scenes.Gone) { lockscreenToGoneTransition() }
            from(
                Scenes.QuickSettings,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                reversed { shadeToQuickSettingsTransition() }
                sharedElement(
                    Notifications.Elements.HeadsUpNotificationPlaceholder,
                    enabled = false,
                )
            }
            from(
                Scenes.Shade,
                to = Scenes.QuickSettings,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                shadeToQuickSettingsTransition()
            }
            from(
                Scenes.Shade,
                to = Scenes.Lockscreen,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                reversed { lockscreenToShadeTransition() }
                sharedElement(Notifications.Elements.NotificationStackPlaceholder, enabled = false)
                sharedElement(
                    Notifications.Elements.HeadsUpNotificationPlaceholder,
                    enabled = false,
                )
            }
            from(
                Scenes.Shade,
                to = Scenes.Lockscreen,
                key = ToSplitShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                reversed { lockscreenToSplitShadeTransition() }
            }
            from(
                Scenes.Communal,
                to = Scenes.Shade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                communalToShadeTransition()
            }

            // Overlay transitions

            to(Overlays.Bouncer) { toBouncerTransition() }
            from(Overlays.Bouncer, to = Scenes.Gone) { bouncerToGoneTransition() }
            from(Scenes.Dream, to = Overlays.Bouncer) { dreamToBouncerTransition() }
            from(Scenes.Lockscreen, to = Overlays.Bouncer) { lockscreenToBouncerTransition() }
            from(
                Scenes.Lockscreen,
                to = Overlays.Bouncer,
                key = TransitionKey.PredictiveBack,
                reversePreview = { bouncerToLockscreenPreview() },
            ) {
                lockscreenToBouncerTransition()
            }
            from(Scenes.Communal, to = Overlays.Bouncer) { communalToBouncerTransition() }
            to(
                Overlays.NotificationsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                toNotificationsShadeTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            to(
                Overlays.QuickSettingsShade,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                toQuickSettingsShadeTransition(
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Gone,
                to = Overlays.NotificationsShade,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                toNotificationsShadeTransition(
                    durationScale = 0.9,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Gone,
                to = Overlays.QuickSettingsShade,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                toQuickSettingsShadeTransition(
                    durationScale = 0.9,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Overlays.NotificationsShade,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_EXPAND_COLLAPSE, // NOTYPO
            ) {
                toNotificationsShadeTransition(
                    durationScale = 0.9,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
            from(
                Scenes.Lockscreen,
                to = Overlays.QuickSettingsShade,
                key = SlightlyFasterShadeCollapse,
                cuj = Cuj.CUJ_NOTIFICATION_SHADE_QS_EXPAND_COLLAPSE, // NOTYPO
            ) {
                toQuickSettingsShadeTransition(
                    durationScale = 0.9,
                    shadeExpansionMotion = shadeExpansionMotion,
                    revealHaptics = revealHaptics,
                )
            }
        }
    }
}
