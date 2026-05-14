package com.phoneagent.companion

/**
 * Hub API Server for companion device connections.
 *
 * PHASE 3+ (Future): This stub defines the interface for the phone acting as
 * a hub that companion devices (tablet, car, web) can connect to.
 *
 * Architecture:
 * - Phone exposes a local REST API (via NanoHTTPD or Android's LocalServerSocket)
 * - Companions authenticate with a shared secret
 * - World model is partitioned: sensitive data stays on phone
 * - Companions get read access to preferences, routines, non-sensitive memories, active goals
 * - Commands from companions go through SafetyGate on the phone first
 *
 * TODO (Phase 3):
 * - Implement HubApiServer using NanoHTTPD
 * - Add device authentication (shared secret + device fingerprint)
 * - Implement world model partitioning (sensitive vs non-sensitive)
 * - Add sync protocol with conflict resolution
 * - Implement companion SDK for Android/web
 *
 * Example endpoints:
 *   GET  /api/v1/profile        → PersonalProfileEntity
 *   GET  /api/v1/goals          → List<GoalEntity>
 *   GET  /api/v1/routines       → List<RoutineEntity>
 *   POST /api/v1/command        → Submit command (goes through SafetyGate)
 *   GET  /api/v1/memories       → Query memories
 *   WS   /api/v1/sync          → Real-time sync channel
 */
object HubApiServer {
    // TODO: Implement in Phase 3
}