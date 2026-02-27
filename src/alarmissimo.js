/**
 * @file alarmissimo.js
 * @description Main application file for Alarmissimo PWA - Progressive Web App alarm clock
 * @author Alarmissimo Development Team
 * @version 1.0.0
 */

/**
 * @class AlarmManager
 * @description Manages alarm-sets, alarm-events, and their execution
 */
class AlarmManager {
  /**
   * @constructor
   * @description Initializes the AlarmManager with empty alarm-sets
   */
  constructor () {
    this.alarmSets = []
    this.timerId = null
    this.lastTriggeredAlarms = new Map()
    // Web Audio API pre-scheduling
    this.audioCtx = null
    this.gongBuffers = new Map() // gongName -> AudioBuffer
    this.scheduledGongs = [] // pre-scheduled AudioBufferSourceNode entries
    this.loadFromStorage()
    this.initPWA()
  }

  /**
   * @function initPWA
   * @description Initializes PWA functionality including service worker registration
   * @returns {Promise<void>}
   */
  async initPWA () {
    if ('serviceWorker' in navigator) {
      try {
        await navigator.serviceWorker.register('./sw.js')
        console.warn('Service Worker registered')
      } catch (error) {
        console.warn('Service Worker registration failed:', error)
      }
    }
  }

  /**
   * @function loadFromStorage
   * @description Loads alarm configurations from localStorage, or creates default configuration on first startup
   * @returns {void}
   */
  loadFromStorage () {
    try {
      const stored = localStorage.getItem('alarmissimo_config')
      if (stored) {
        this.alarmSets = JSON.parse(stored)
      } else {
        // Create default configuration on first startup
        this.createDefaultConfiguration()
      }
    } catch (error) {
      console.error('Failed to load configuration from localStorage:', error)
      this.alarmSets = []
    }
  }

  /**
   * @function createDefaultConfiguration
   * @description Creates a default demo alarm configuration for first-time users
   * @returns {void}
   */
  createDefaultConfiguration () {
    const demoAlarmSet = {
      id: Date.now(),
      name: 'Demo',
      enabled: true,
      weekdays: [1, 2, 3, 4, 5], // Monday to Friday
      audioVolume: 80,
      alarmEvents: [
        {
          id: Date.now() + 1,
          time: '07:30',
          gong: 'gong',
          timePlayback: true,
          message: 'John, es ist Zeit, die Schuhe anzuziehen.'
        }
      ]
    }
    this.alarmSets = [demoAlarmSet]
    this.saveToStorage()
  }

  /**
   * @function saveToStorage
   * @description Saves alarm configurations to localStorage
   * @returns {void}
   */
  saveToStorage () {
    try {
      localStorage.setItem('alarmissimo_config', JSON.stringify(this.alarmSets))
      // Reschedule gongs so any config change is reflected immediately
      this.scheduleAllAlarms()
    } catch (error) {
      if (error.name === 'QuotaExceededError') {
        console.warn('localStorage quota exceeded')
        alert('Speicherplatz im Browser ist voll. Bitte löschen Sie einige Wecker.')
      } else {
        console.error('Failed to save configuration to localStorage:', error)
      }
    }
  }

  /**
   * @function createAlarmSet
   * @description Creates a new alarm-set with default properties
   * @param {string} name - The name of the alarm-set (1-30 characters)
   * @returns {Object} The created alarm-set object
   */
  createAlarmSet (name = '') {
    const alarmSet = {
      id: Date.now(),
      name: name.substring(0, 30),
      enabled: true,
      weekdays: [0, 1, 2, 3, 4, 5, 6],
      audioVolume: 80,
      alarmEvents: []
    }
    this.alarmSets.push(alarmSet)
    this.saveToStorage()
    return alarmSet
  }

  /**
   * @function deleteAlarmSet
   * @description Deletes an alarm-set by ID
   * @param {number} alarmSetId - The ID of the alarm-set to delete
   * @returns {boolean} True if deleted, false if not found
   */
  deleteAlarmSet (alarmSetId) {
    const index = this.alarmSets.findIndex(set => set.id === alarmSetId)
    if (index !== -1) {
      this.alarmSets.splice(index, 1)
      this.saveToStorage()
      return true
    }
    return false
  }

  /**
   * @function duplicateAlarmSet
   * @description Creates a copy of an existing alarm-set
   * @param {number} alarmSetId - The ID of the alarm-set to duplicate
   * @returns {Object|null} The duplicated alarm-set or null if not found
   */
  duplicateAlarmSet (alarmSetId) {
    const original = this.alarmSets.find(set => set.id === alarmSetId)
    if (!original) {
      return null
    }
    const copy = JSON.parse(JSON.stringify(original))
    copy.id = Date.now()
    copy.name = `${copy.name} (Kopie)`
    this.alarmSets.push(copy)
    this.saveToStorage()
    return copy
  }

  /**
   * @function createAlarmEvent
   * @description Creates a new alarm-event within an alarm-set
   * @param {number} alarmSetId - The ID of the parent alarm-set
   * @param {string} time - The time in HH:MM format
   * @returns {Object|null} The created alarm-event or null if alarm-set not found
   */
  createAlarmEvent (alarmSetId, time = '00:00') {
    const alarmSet = this.alarmSets.find(set => set.id === alarmSetId)
    if (!alarmSet) {
      return null
    }
    const alarmEvent = {
      id: Date.now(),
      time,
      gong: 'none',
      timePlayback: true,
      message: ''
    }
    alarmSet.alarmEvents.push(alarmEvent)
    alarmSet.alarmEvents.sort((a, b) => this.timeToMinutes(a.time) - this.timeToMinutes(b.time))
    this.saveToStorage()
    return alarmEvent
  }

  /**
   * @function deleteAlarmEvent
   * @description Deletes an alarm-event from an alarm-set
   * @param {number} alarmSetId - The ID of the parent alarm-set
   * @param {number} eventId - The ID of the alarm-event to delete
   * @returns {boolean} True if deleted, false if not found
   */
  deleteAlarmEvent (alarmSetId, eventId) {
    const alarmSet = this.alarmSets.find(set => set.id === alarmSetId)
    if (!alarmSet) {
      return false
    }
    const index = alarmSet.alarmEvents.findIndex(event => event.id === eventId)
    if (index !== -1) {
      alarmSet.alarmEvents.splice(index, 1)
      this.saveToStorage()
      return true
    }
    return false
  }

  /**
   * @function duplicateAlarmEvent
   * @description Creates a copy of an existing alarm-event
   * @param {number} alarmSetId - The ID of the parent alarm-set
   * @param {number} eventId - The ID of the alarm-event to duplicate
   * @returns {Object|null} The duplicated alarm-event or null if not found
   */
  duplicateAlarmEvent (alarmSetId, eventId) {
    const alarmSet = this.alarmSets.find(set => set.id === alarmSetId)
    if (!alarmSet) {
      return null
    }
    const original = alarmSet.alarmEvents.find(event => event.id === eventId)
    if (!original) {
      return null
    }
    const copy = JSON.parse(JSON.stringify(original))
    copy.id = Date.now()
    alarmSet.alarmEvents.push(copy)
    alarmSet.alarmEvents.sort((a, b) => this.timeToMinutes(a.time) - this.timeToMinutes(b.time))
    this.saveToStorage()
    return copy
  }

  /**
   * @function timeToMinutes
   * @description Converts time string (HH:MM) to minutes since midnight
   * @param {string} time - Time in HH:MM format
   * @returns {number} Minutes since midnight
   */
  timeToMinutes (time) {
    const [hours, minutes] = time.split(':').map(Number)
    return hours * 60 + minutes
  }

  /**
   * @function getUpcomingAlarms
   * @description Returns alarm-events scheduled for the next 24 hours
   * @returns {Array<Object>} Array of upcoming alarm-events with parent alarm-set info
   */
  getUpcomingAlarms () {
    const now = new Date()
    const currentMinutes = now.getHours() * 60 + now.getMinutes()
    const currentDayOfWeek = now.getDay()
    const upcoming = []

    this.alarmSets.forEach(alarmSet => {
      if (!alarmSet.enabled) {
        return
      }
      alarmSet.alarmEvents.forEach(event => {
        const eventMinutes = this.timeToMinutes(event.time)
        let isUpcoming = false

        if (eventMinutes >= currentMinutes && alarmSet.weekdays.includes(currentDayOfWeek)) {
          isUpcoming = true
        } else if (eventMinutes < currentMinutes) {
          const tomorrow = (currentDayOfWeek + 1) % 7
          if (alarmSet.weekdays.includes(tomorrow)) {
            isUpcoming = true
          }
        }

        if (isUpcoming) {
          upcoming.push({
            alarmSetId: alarmSet.id,
            alarmSetName: alarmSet.name,
            alarmEvent: event,
            audioVolume: alarmSet.audioVolume,
            order: eventMinutes >= currentMinutes ? eventMinutes : 1440 + eventMinutes
          })
        }
      })
    })

    return upcoming.sort((a, b) => a.order - b.order)
  }

  /**
   * @function playAlarm
   * @description Plays an alarm: gong (unless pre-scheduled) → time announcement → message.
   * When isTest=true, gong is always played immediately via Audio element.
   * When isTest=false (triggered by checkAlarms), gong is skipped because it was
   * already pre-scheduled via the Web Audio API. Falls back to Audio element if
   * no AudioContext is available.
   * @param {Object} alarmEvent - The alarm-event to play
   * @param {number} audioVolume - Volume level (0-100)
   * @param {boolean} [isTest=false] - If true, plays gong immediately (test mode)
   * @returns {Promise<void>}
   */
  async playAlarm (alarmEvent, audioVolume, isTest = false) {
    if (!isTest) {
      // Mark as triggered immediately (before await) to prevent re-triggering
      // during the async playback while the interval fires again
      this.lastTriggeredAlarms.set(alarmEvent.id, Date.now())
    }
    console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} ALARM TRIGGERED: id=${alarmEvent.id} time=${alarmEvent.time} gong=${alarmEvent.gong} message="${alarmEvent.message}" isTest=${isTest}`)

    try {
      // Step 1: Gong
      if (alarmEvent.gong && alarmEvent.gong !== 'none') {
        if (isTest || !this.audioCtx) {
          // Test mode or no AudioContext: play immediately via Audio element
          console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Playing gong immediately (${isTest ? 'test' : 'no AudioContext'}): ${alarmEvent.gong}`)
          await this.playGong(alarmEvent.gong, audioVolume)
          console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Gong finished`)
        } else {
          // Pre-scheduled via Web Audio API – already playing, nothing to do here
          console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Gong "${alarmEvent.gong}" was pre-scheduled via Web Audio API`)
        }
      } else {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} No gong selected, skipping`)
      }

      // Step 2: Announce time if enabled
      if (alarmEvent.timePlayback) {
        const now = new Date()
        const hours = String(now.getHours())
        const minutes = now.getMinutes() === 0 ? '' : ` ${String(now.getMinutes())}`
        const timeText = `Es ist ${hours} Uhr${minutes}.`
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Speaking time: "${timeText}"`)
        await this.speak(timeText, audioVolume)
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Time announcement finished`)
      }

      // Step 3: Speak message if there is any text
      if (alarmEvent.message && alarmEvent.message.trim()) {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Speaking message: "${alarmEvent.message}"`)
        await this.speak(alarmEvent.message, audioVolume)
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Message finished`)
      }

      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Alarm sequence complete for event id=${alarmEvent.id}`)

      if (!isTest) {
        // Reschedule gong for next occurrence (next day / next week)
        this.scheduleAllAlarms()
      }
    } catch (error) {
      console.error('[Alarmissimo] Error playing alarm:', error)
    }
  }

  /**
   * @function getGongFilePath
   * @description Maps gong names to file paths
   * @param {string} gongName - The gong identifier (bikebell, doorbell, kettle, gong, or custom path)
   * @returns {string|null} The file path or null if no gong should play
   */
  getGongFilePath (gongName) {
    if (!gongName || gongName === 'none') {
      return null
    }

    const gongMap = {
      bikebell: './sound/bikebell.mp3',
      doorbell: './sound/doorbell.mp3',
      kettle: './sound/kettle.mp3',
      gong: './sound/gong.mp3'
    }

    // If it's a predefined gong name, map it to file path
    if (gongMap[gongName]) {
      return gongMap[gongName]
    }

    // Otherwise, assume it's a custom file path (e.g., data URL from file upload)
    return gongName
  }

  /**
   * @function playGong
   * @description Plays the gong sound file
   * @param {string} gongName - The gong identifier or file path
   * @param {number} audioVolume - Volume level (0-100)
   * @returns {Promise<void>}
   */
  async playGong (gongName, audioVolume) {
    return new Promise((resolve) => {
      const gongFile = this.getGongFilePath(gongName)

      // If no gong file, skip
      if (!gongFile) {
        resolve()
        return
      }

      const audio = new Audio(gongFile)
      audio.volume = Math.min(audioVolume / 100, 1.0)

      audio.onended = () => {
        resolve()
      }

      audio.onerror = (error) => {
        console.warn('Failed to play gong:', gongName, error)
        resolve() // Continue with voice announcement even if gong fails
      }

      audio.play().catch(error => {
        console.warn('Audio playback failed:', gongName, error)
        resolve() // Continue with voice announcement even if gong fails
      })
    })
  }

  /**
   * @function speak
   * @description Uses Web Speech API to speak text with automatic voice selection
   * @param {string} text - The text to speak
   * @param {number} audioVolume - Volume level (0-100)
   * @returns {Promise<void>}
   */
  async speak (text, audioVolume) {
    return new Promise((resolve) => {
      if (!('speechSynthesis' in window)) {
        console.warn('Speech Synthesis not supported - skipping audio announcement')
        resolve()
        return
      }

      const utterance = new SpeechSynthesisUtterance(text)
      utterance.volume = Math.min(audioVolume / 100, 1.0)
      utterance.rate = 1.0
      utterance.pitch = 1.0
      utterance.lang = 'de-DE' // Always set German language

      // Try to use German voice if available
      const voices = speechSynthesis.getVoices()
      if (voices.length > 0) {
        const germanVoice = voices.find(v => v.lang.startsWith('de'))
        if (germanVoice) {
          utterance.voice = germanVoice
        } else if (voices[0]) {
          utterance.voice = voices[0]
        }
      }

      // Set a timeout to resolve if speech hangs
      const timeoutId = setTimeout(() => {
        console.warn('Speech synthesis timeout - resolving')
        speechSynthesis.cancel()
        resolve()
      }, 5000) // 5 second timeout

      utterance.onend = () => {
        clearTimeout(timeoutId)
        resolve()
      }

      utterance.onerror = (event) => {
        // 'interrupted' is raised when cancel() is called on a speaking utterance — not a real error
        if (event.error === 'interrupted' || event.error === 'canceled') {
          clearTimeout(timeoutId)
          resolve()
          return
        }
        clearTimeout(timeoutId)
        console.warn('Speech synthesis error - continuing anyway:', event.error)
        resolve()
      }

      // Cancel any ongoing speech, then wait one JS tick before starting the new
      // utterance. Without the delay, Android raises synthesis-failed because the
      // synthesis engine hasn't finished tearing down the cancelled utterance.
      speechSynthesis.cancel()
      setTimeout(() => {
        try {
          speechSynthesis.speak(utterance)
        } catch (error) {
          clearTimeout(timeoutId)
          console.warn('Failed to start speech synthesis:', error)
          resolve()
        }
      }, 50)
    })
  }

  /**
   * @function initAudioContext
   * @description Creates the Web Audio API context and keeps it alive with a silent oscillator.
   * Must be called from a user-gesture handler to satisfy autoplay policy.
   * After init, preloads gong buffers and pre-schedules all upcoming alarms.
   * @returns {void}
   */
  initAudioContext () {
    if (this.audioCtx) {
      // Resume if suspended (e.g. after page regained focus)
      if (this.audioCtx.state === 'suspended') {
        this.audioCtx.resume().then(() => {
          console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} AudioContext resumed`)
          this.scheduleAllAlarms()
        })
      }
      return
    }

    this.audioCtx = new AudioContext()
    console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} AudioContext created, state=${this.audioCtx.state}`)

    // Silent oscillator keeps the AudioContext running and prevents Android from suspending it
    const silentGain = this.audioCtx.createGain()
    silentGain.gain.value = 0
    const osc = this.audioCtx.createOscillator()
    osc.connect(silentGain)
    silentGain.connect(this.audioCtx.destination)
    osc.start()

    this.audioCtx.addEventListener('statechange', () => {
      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} AudioContext state changed: ${this.audioCtx.state}`)
      if (this.audioCtx.state === 'running') {
        this.scheduleAllAlarms()
      }
    })

    this.preloadGongBuffers().then(() => this.scheduleAllAlarms())
  }

  /**
   * @function preloadGongBuffers
   * @description Fetches and decodes all built-in gong sound files into AudioBuffers
   * so they are ready for zero-latency pre-scheduled playback.
   * @returns {Promise<void>}
   */
  async preloadGongBuffers () {
    const gongMap = {
      bikebell: './sound/bikebell.mp3',
      doorbell: './sound/doorbell.mp3',
      kettle: './sound/kettle.mp3',
      gong: './sound/gong.mp3'
    }
    for (const [name, path] of Object.entries(gongMap)) {
      try {
        const response = await fetch(path)
        const arrayBuffer = await response.arrayBuffer()
        const audioBuffer = await this.audioCtx.decodeAudioData(arrayBuffer)
        this.gongBuffers.set(name, audioBuffer)
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Buffer loaded: ${name}`)
      } catch (err) {
        console.warn(`Failed to preload gong buffer "${name}":`, err.message)
      }
    }
  }

  /**
   * @function secondsUntilNextAlarm
   * @description Calculates the seconds from now until the next scheduled firing
   * of an alarm event, considering weekday constraints.
   * @param {Object} event - AlarmEvent with .time (HH:MM)
   * @param {Array<number>} weekdays - Array of weekday indices (0=Sun)
   * @returns {number|null} Seconds until next firing, or null if none within 7 days
   */
  secondsUntilNextAlarm (event, weekdays) {
    const now = new Date()
    const eventMinutes = this.timeToMinutes(event.time)

    for (let daysAhead = 0; daysAhead < 7; daysAhead++) {
      const dayOfWeek = (now.getDay() + daysAhead) % 7
      if (!weekdays.includes(dayOfWeek)) continue

      const target = new Date(now)
      target.setDate(target.getDate() + daysAhead)
      target.setHours(Math.floor(eventMinutes / 60), eventMinutes % 60, 0, 0)

      const secondsUntil = (target.getTime() - now.getTime()) / 1000
      if (secondsUntil > 0) {
        return secondsUntil
      }
    }
    return null
  }

  /**
   * @function scheduleAllAlarms
   * @description Pre-schedules gong playback for all upcoming alarm events using
   * the Web Audio API timing system. Cancels any existing scheduled gongs first.
   * Only schedules alarms within the next 24 hours.
   * @returns {void}
   */
  scheduleAllAlarms () {
    if (!this.audioCtx || this.audioCtx.state !== 'running') {
      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} scheduleAllAlarms: AudioContext not ready (${this.audioCtx ? this.audioCtx.state : 'null'})`)
      return
    }

    this.cancelScheduledGongs()
    const maxSeconds = 24 * 3600

    this.alarmSets.forEach(alarmSet => {
      if (!alarmSet.enabled) return

      alarmSet.alarmEvents.forEach(event => {
        if (!event.gong || event.gong === 'none') return

        const buffer = this.gongBuffers.get(event.gong)
        if (!buffer) {
          console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} No buffer for gong "${event.gong}" – will fall back to Audio element`)
          return
        }

        const secondsUntil = this.secondsUntilNextAlarm(event, alarmSet.weekdays)
        if (secondsUntil === null || secondsUntil > maxSeconds) {
          console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Event id=${event.id}: not scheduled within 24h (secondsUntil=${secondsUntil})`)
          return
        }

        const source = this.audioCtx.createBufferSource()
        source.buffer = buffer
        const gainNode = this.audioCtx.createGain()
        gainNode.gain.value = Math.min(alarmSet.audioVolume / 100, 1.0)
        source.connect(gainNode)
        gainNode.connect(this.audioCtx.destination)

        const startAt = this.audioCtx.currentTime + secondsUntil
        source.start(startAt)

        this.scheduledGongs.push({ source, gainNode, eventId: event.id })
        const eta = new Date(Date.now() + secondsUntil * 1000).toLocaleTimeString('de-DE')
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Gong "${event.gong}" pre-scheduled for event id=${event.id} in ${Math.round(secondsUntil)}s (at ${eta})`)
      })
    })

    console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} scheduleAllAlarms: ${this.scheduledGongs.length} gong(s) scheduled`)
  }

  /**
   * @function cancelScheduledGongs
   * @description Stops and disconnects all previously pre-scheduled gong sources.
   * @returns {void}
   */
  cancelScheduledGongs () {
    this.scheduledGongs.forEach(({ source }) => {
      try { source.stop() } catch (_) {}
      source.disconnect()
    })
    const count = this.scheduledGongs.length
    this.scheduledGongs = []
    console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} cancelScheduledGongs: cancelled ${count} gong(s)`)
  }

  /**
   * @function testAlarm
   * @description Plays an alarm immediately for testing purposes
   * @param {Object} alarmEvent - The alarm-event to test
   * @param {number} audioVolume - Volume level (0-100)
   * @returns {Promise<void>}
   */
  async testAlarm (alarmEvent, audioVolume) {
    await this.playAlarm(alarmEvent, audioVolume, true)
  }

  /**
   * @function startAlarmCheck
   * @description Starts the background timer to check for triggered alarms
   * @returns {void}
   */
  startAlarmCheck () {
    console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} startAlarmCheck: starting 10s interval`)

    // Acquire Screen Wake Lock to prevent Android from throttling/suspending the timer
    this.acquireWakeLock()

    // Check every 10 seconds to avoid missing a minute due to timer drift
    this.timerId = setInterval(() => {
      this.checkAlarms()
    }, 10000)
    // Initial check
    this.checkAlarms()

    // Re-check immediately when tab becomes visible again (e.g. after background throttling)
    document.addEventListener('visibilitychange', () => {
      const state = document.visibilityState
      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} visibilitychange: ${state}`)
      if (state === 'visible') {
        // Re-acquire wake lock after tab comes back to foreground
        this.acquireWakeLock()
        // Resume AudioContext and re-schedule (drift correction after background throttling)
        this.initAudioContext()
        this.checkAlarms()
      } else {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Tab hidden - interval may be throttled on Android`)
      }
    })
  }

  /**
   * @function acquireWakeLock
   * @description Requests a Screen Wake Lock to prevent Android from throttling timers
   * @returns {void}
   */
  acquireWakeLock () {
    if (!('wakeLock' in navigator)) {
      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Wake Lock API not supported on this device/browser`)
      return
    }
    navigator.wakeLock.request('screen').then(lock => {
      this.wakeLock = lock
      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Wake Lock acquired`)
      lock.addEventListener('release', () => {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Wake Lock released (system took it back)`)
      })
    }).catch(err => {
      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} Wake Lock request failed:`, err.message)
    })
  }

  /**
   * @function checkAlarms
   * @description Checks if any alarms should be triggered now
   * @returns {void}
   */
  checkAlarms () {
    const now = new Date()
    const currentTime = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0')
    const currentDayOfWeek = now.getDay()
    const dayNames = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']
    console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')} checkAlarms: time=${currentTime} day=${dayNames[currentDayOfWeek]} sets=${this.alarmSets.length}`)

    this.alarmSets.forEach(alarmSet => {
      if (!alarmSet.enabled) {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')}   AlarmSet "${alarmSet.name}": SKIPPED (disabled)`)
        return
      }

      if (!alarmSet.weekdays.includes(currentDayOfWeek)) {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')}   AlarmSet "${alarmSet.name}": SKIPPED (not scheduled on ${dayNames[currentDayOfWeek]}, scheduled: ${alarmSet.weekdays.map(d => dayNames[d]).join(',')})`)
        return
      }

      console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')}   AlarmSet "${alarmSet.name}": checking ${alarmSet.alarmEvents.length} event(s)`)

      // Get all events that should trigger now
      const eventsToTrigger = alarmSet.alarmEvents.filter(event => {
        const timeMatch = event.time === currentTime
        const notYetTriggered = !this.lastTriggeredAlarms.has(event.id)
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')}     Event id=${event.id} time=${event.time}: timeMatch=${timeMatch} notYetTriggered=${notYetTriggered}`)
        return timeMatch && notYetTriggered
      })

      if (eventsToTrigger.length === 0) {
        console.debug(`[Alarmissimo] ${new Date().toLocaleTimeString('de-DE')}   AlarmSet "${alarmSet.name}": no events to trigger`)
      }

      // Play in sequence
      eventsToTrigger.forEach(event => {
        this.playAlarm(event, alarmSet.audioVolume)
      })
    })

    // Clean up old triggered alarms (older than 24 hours)
    const oneDayAgo = Date.now() - (24 * 60 * 60 * 1000)
    for (const [eventId, timestamp] of this.lastTriggeredAlarms) {
      if (timestamp < oneDayAgo) {
        this.lastTriggeredAlarms.delete(eventId)
      }
    }
  }

  /**
   * @function stopAlarmCheck
   * @description Stops the background alarm check timer
   * @returns {void}
   */
  stopAlarmCheck () {
    if (this.timerId) {
      clearInterval(this.timerId)
      this.timerId = null
    }
  }
}

/**
 * @class AppController
 * @description Manages UI navigation and app state for Alarmissimo
 */
class AppController {
  /**
   * @constructor
   * @description Initializes the app controller with alarm manager
   */
  constructor () {
    this.manager = window.alarmManager || new AlarmManager()
    window.alarmManager = this.manager

    this.currentPage = 'dashboard'
    this.currentAlarmSetId = null
    this.currentAlarmEventId = null
    this.customGongFile = null

    this.setupEventListeners()
    this.initializePageVisibility()
    this.updateDashboard()
    this.manager.startAlarmCheck()

    // Initialize Web Audio API on first user gesture (required by autoplay policy)
    // Once the AudioContext is created, gong sounds will be pre-scheduled
    const initAudio = () => {
      this.manager.initAudioContext()
    }
    document.addEventListener('click', initAudio, { once: true })
    document.addEventListener('touchstart', initAudio, { once: true })

    // Update dashboard every 30 seconds to keep remaining time accurate
    setInterval(() => this.updateDashboard(), 30000)
  }

  /**
   * @function initializePageVisibility
   * @description Sets initial page visibility (dashboard visible, others hidden)
   * @returns {void}
   */
  initializePageVisibility () {
    const dashPage = document.getElementById('dashboard-page')
    const configPage = document.getElementById('config-page')
    const setEditorPage = document.getElementById('alarm-set-editor-page')
    const eventEditorPage = document.getElementById('alarm-event-editor-page')

    dashPage.classList.add('visible')
    dashPage.classList.remove('hidden')

    configPage.classList.add('hidden')
    configPage.classList.remove('visible')

    setEditorPage.classList.add('hidden')
    setEditorPage.classList.remove('visible')

    eventEditorPage.classList.add('hidden')
    eventEditorPage.classList.remove('visible')
  }

  /**
   * @function setupEventListeners
   * @description Sets up event listeners for form inputs
   * @returns {void}
   */
  setupEventListeners () {
    // Alarm Set Editor listeners
    const nameInput = document.getElementById('alarm-set-name')
    if (nameInput) {
      nameInput.addEventListener('input', (e) => {
        const counter = document.getElementById('name-char-counter')
        if (counter) {
          counter.textContent = `${e.target.value.length}/30`
        }
      })
    }

    const volumeInput = document.getElementById('alarm-set-volume')
    if (volumeInput) {
      volumeInput.addEventListener('input', (e) => {
        const display = document.getElementById('volume-display')
        if (display) {
          display.textContent = `${e.target.value}%`
        }
      })
    }

    // Alarm Event Editor listeners
    const messageInput = document.getElementById('alarm-event-message')
    if (messageInput) {
      messageInput.addEventListener('input', (e) => {
        const counter = document.getElementById('message-char-counter')
        if (counter) {
          counter.textContent = `${e.target.value.length}/300`
        }
      })
    }

    const gongSelect = document.getElementById('alarm-event-gong')
    if (gongSelect) {
      gongSelect.addEventListener('change', (e) => {
        const customItem = document.getElementById('custom-gong-item')
        if (customItem) {
          customItem.style.display = e.target.value === 'custom' ? 'block' : 'none'
        }
      })
    }

    const gongFile = document.getElementById('alarm-event-gong-file')
    if (gongFile) {
      gongFile.addEventListener('change', (e) => {
        if (e.target.files[0]) {
          const reader = new FileReader()
          reader.onload = (event) => {
            this.customGongFile = event.target.result
          }
          reader.readAsDataURL(e.target.files[0])
        }
      })
    }
  }

  /**
   * @function openMenu
   * @description Opens the side menu
   * @returns {void}
   */
  openMenu () {
    const menu = document.querySelector('ons-splitter-side')
    if (menu && menu.open) {
      menu.open()
    }
  }

  /**
   * @function closeMenu
   * @description Closes the side menu
   * @returns {void}
   */
  closeMenu () {
    const menu = document.querySelector('ons-splitter-side')
    if (menu && menu.close) {
      menu.close()
    }
  }

  /**
   * @function openDashboard
   * @description Opens the dashboard page
   * @returns {void}
   */
  openDashboard () {
    this.currentPage = 'dashboard'
    const dashPage = document.getElementById('dashboard-page')
    const configPage = document.getElementById('config-page')
    const setEditorPage = document.getElementById('alarm-set-editor-page')
    const eventEditorPage = document.getElementById('alarm-event-editor-page')

    dashPage.classList.remove('hidden')
    dashPage.classList.add('visible')

    configPage.classList.add('hidden')
    configPage.classList.remove('visible')

    setEditorPage.classList.add('hidden')
    setEditorPage.classList.remove('visible')

    eventEditorPage.classList.add('hidden')
    eventEditorPage.classList.remove('visible')

    this.updateDashboard()
    this.closeMenu()
  }

  /**
   * @function openConfiguration
   * @description Opens the configuration page
   * @returns {void}
   */
  openConfiguration () {
    this.currentPage = 'config'
    const dashPage = document.getElementById('dashboard-page')
    const configPage = document.getElementById('config-page')
    const setEditorPage = document.getElementById('alarm-set-editor-page')
    const eventEditorPage = document.getElementById('alarm-event-editor-page')

    dashPage.classList.add('hidden')
    dashPage.classList.remove('visible')

    configPage.classList.remove('hidden')
    configPage.classList.add('visible')

    setEditorPage.classList.add('hidden')
    setEditorPage.classList.remove('visible')

    eventEditorPage.classList.add('hidden')
    eventEditorPage.classList.remove('visible')

    this.updateConfigScreen()
    this.closeMenu()
  }

  /**
   * @function openAlarmSetEditor
   * @description Opens the alarm set editor (new or existing)
   * @param {number} alarmSetId - Optional ID of alarm set to edit
   * @returns {void}
   */
  openAlarmSetEditor (alarmSetId = null) {
    this.currentAlarmSetId = alarmSetId
    this.currentPage = 'alarm-set-editor'

    const dashPage = document.getElementById('dashboard-page')
    const configPage = document.getElementById('config-page')
    const setEditorPage = document.getElementById('alarm-set-editor-page')
    const eventEditorPage = document.getElementById('alarm-event-editor-page')

    dashPage.classList.add('hidden')
    dashPage.classList.remove('visible')

    configPage.classList.add('hidden')
    configPage.classList.remove('visible')

    setEditorPage.classList.remove('hidden')
    setEditorPage.classList.add('visible')

    eventEditorPage.classList.add('hidden')
    eventEditorPage.classList.remove('visible')

    if (alarmSetId) {
      this.loadAlarmSetEditor(alarmSetId)
    } else {
      this.newAlarmSet()
    }
  }

  /**
   * @function newAlarmSet
   * @description Prepares editor for a new alarm set
   * @returns {void}
   */
  newAlarmSet () {
    document.getElementById('alarm-set-editor-title').textContent = 'Neue Weckergruppe'
    document.getElementById('alarm-set-name').value = ''
    document.getElementById('alarm-set-enabled').checked = true
    document.getElementById('alarm-set-volume').value = 80
    document.getElementById('volume-display').textContent = '80%'
    document.getElementById('name-char-counter').textContent = '0/30'
    this.renderWeekdaysChips([0, 1, 2, 3, 4, 5, 6])
    this.renderAlarmEvents([])
  }

  /**
   * @function loadAlarmSetEditor
   * @description Loads an existing alarm set for editing
   * @param {number} alarmSetId - The ID of the alarm set to load
   * @returns {void}
   */
  loadAlarmSetEditor (alarmSetId) {
    const alarmSet = this.manager.alarmSets.find(set => set.id === alarmSetId)
    if (!alarmSet) {
      return
    }

    document.getElementById('alarm-set-editor-title').textContent = 'Weckergruppe bearbeiten'
    document.getElementById('alarm-set-name').value = alarmSet.name
    document.getElementById('alarm-set-enabled').checked = alarmSet.enabled
    document.getElementById('alarm-set-volume').value = alarmSet.audioVolume
    document.getElementById('volume-display').textContent = `${alarmSet.audioVolume}%`
    document.getElementById('name-char-counter').textContent = `${alarmSet.name.length}/30`
    this.renderWeekdaysChips(alarmSet.weekdays)
    this.renderAlarmEvents(alarmSet.alarmEvents)
  }

  /**
   * @function renderWeekdaysChips
   * @description Renders the weekday selection chips with Monday as first day
   * @param {Array<number>} selectedWeekdays - Array of selected weekday indices
   * @returns {void}
   */
  renderWeekdaysChips (selectedWeekdays) {
    const container = document.getElementById('weekdays-container')
    const weekdays = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
    const weekdayIndices = [1, 2, 3, 4, 5, 6, 0] // Maps to JavaScript getDay() values (1=Monday, 0=Sunday)

    container.innerHTML = weekdays.map((day, position) => {
      const weekdayIndex = weekdayIndices[position]
      const isSelected = selectedWeekdays.includes(weekdayIndex)
      return `
        <div class="weekday-chip ${isSelected ? 'active' : ''}" 
             data-weekday="${weekdayIndex}"
             onclick="app.toggleWeekday(${weekdayIndex})">
          ${day}
        </div>
      `
    }).join('')
  }

  /**
   * @function toggleWeekday
   * @description Toggles selection of a weekday
   * @param {number} weekday - The weekday index (0-6)
   * @returns {void}
   */
  toggleWeekday (weekday) {
    const chips = document.querySelectorAll('.weekday-chip')
    const chip = Array.from(chips).find(c => parseInt(c.dataset.weekday) === weekday)

    if (chip) {
      chip.classList.toggle('active')
    }
  }

  /**
   * @function getSelectedWeekdays
   * @description Gets the currently selected weekdays
   * @returns {Array<number>} Array of selected weekday indices
   */
  getSelectedWeekdays () {
    const chips = document.querySelectorAll('.weekday-chip.active')
    return Array.from(chips).map(chip => parseInt(chip.dataset.weekday))
  }

  /**
   * @function renderAlarmEvents
   * @description Renders the list of alarm events for the current alarm set
   * @param {Array<Object>} alarmEvents - Array of alarm event objects
   * @returns {void}
   */
  renderAlarmEvents (alarmEvents) {
    const container = document.getElementById('alarm-events-editor')

    if (alarmEvents.length === 0) {
      container.innerHTML = '<p class="no-alarms">Keine Alarme definiert</p>'
      return
    }

    container.innerHTML = alarmEvents.map(event => `
      <div class="alarm-event-item">
        <div class="alarm-event-info">
          <div class="alarm-event-time">${event.time}</div>
          <div class="alarm-event-message">${event.message || '(keine Nachricht)'}</div>
        </div>
        <div class="alarm-event-actions">
          <button class="action-button icon-only" onclick="app.editAlarmEvent(${event.id})" title="Bearbeiten"><ons-icon icon="md-edit"></ons-icon></button>
          <button class="action-button icon-only" onclick="app.duplicateAlarmEventQuick(${event.id})" title="Kopieren"><ons-icon icon="md-copy"></ons-icon></button>
          <button class="action-button icon-only danger" onclick="app.deleteAlarmEventConfirm(${event.id})" title="Löschen"><ons-icon icon="md-delete"></ons-icon></button>
        </div>
      </div>
    `).join('')
  }

  /**
   * @function addAlarmEvent
   * @description Opens the editor for a new alarm event
   * @returns {void}
   */
  addAlarmEvent () {
    if (!this.currentAlarmSetId) {
      alert('Bitte speichern Sie die Weckergruppe zuerst')
      return
    }

    // Create new event
    const alarmSet = this.manager.alarmSets.find(set => set.id === this.currentAlarmSetId)
    if (!alarmSet) {
      return
    }

    const newEvent = this.manager.createAlarmEvent(this.currentAlarmSetId, '00:00')
    if (newEvent) {
      this.editAlarmEvent(newEvent.id)
    }
  }

  /**
   * @function editAlarmEvent
   * @description Opens the alarm event editor for an existing event
   * @param {number} eventId - The ID of the event to edit
   * @returns {void}
   */
  editAlarmEvent (eventId) {
    const alarmSet = this.manager.alarmSets.find(set => set.id === this.currentAlarmSetId)
    if (!alarmSet) {
      return
    }

    const event = alarmSet.alarmEvents.find(e => e.id === eventId)
    if (!event) {
      return
    }

    this.currentAlarmEventId = eventId
    this.currentPage = 'alarm-event-editor'

    const dashPage = document.getElementById('dashboard-page')
    const configPage = document.getElementById('config-page')
    const setEditorPage = document.getElementById('alarm-set-editor-page')
    const eventEditorPage = document.getElementById('alarm-event-editor-page')

    dashPage.classList.add('hidden')
    dashPage.classList.remove('visible')

    configPage.classList.add('hidden')
    configPage.classList.remove('visible')

    setEditorPage.classList.add('hidden')
    setEditorPage.classList.remove('visible')

    eventEditorPage.classList.remove('hidden')
    eventEditorPage.classList.add('visible')

    document.getElementById('alarm-event-editor-title').textContent = 'Alarm bearbeiten'
    document.getElementById('alarm-event-time').value = event.time
    document.getElementById('alarm-event-timeplayback').checked = event.timePlayback
    document.getElementById('alarm-event-gong').value = event.gong
    document.getElementById('alarm-event-message').value = event.message
    document.getElementById('message-char-counter').textContent = `${event.message.length}/300`

    // Handle custom gong
    const customItem = document.getElementById('custom-gong-item')
    if (event.gong === 'custom') {
      customItem.style.display = 'block'
    } else {
      customItem.style.display = 'none'
    }
  }

  /**
   * @function deleteAlarmEventConfirm
   * @description Shows confirmation before deleting an alarm event
   * @param {number} eventId - The ID of the event to delete
   * @returns {void}
   */
  deleteAlarmEventConfirm (eventId) {
    if (confirm('Alarm wirklich löschen?')) {
      this.manager.deleteAlarmEvent(this.currentAlarmSetId, eventId)
      this.loadAlarmSetEditor(this.currentAlarmSetId)
    }
  }

  /**
   * @function saveAlarmEvent
   * @description Saves the current alarm event
   * @returns {void}
   */
  saveAlarmEvent () {
    const alarmSet = this.manager.alarmSets.find(set => set.id === this.currentAlarmSetId)
    if (!alarmSet) {
      return
    }

    const event = alarmSet.alarmEvents.find(e => e.id === this.currentAlarmEventId)
    if (!event) {
      return
    }

    event.time = document.getElementById('alarm-event-time').value || '00:00'
    event.timePlayback = document.getElementById('alarm-event-timeplayback').checked
    event.message = document.getElementById('alarm-event-message').value

    const gongSelect = document.getElementById('alarm-event-gong').value
    if (gongSelect === 'custom' && this.customGongFile) {
      event.gong = this.customGongFile
    } else {
      event.gong = gongSelect
    }

    // Re-sort events by time
    alarmSet.alarmEvents.sort((a, b) => this.manager.timeToMinutes(a.time) - this.manager.timeToMinutes(b.time))

    this.manager.saveToStorage()
    this.openAlarmSetEditor(this.currentAlarmSetId)
  }

  /**
   * @function deleteAlarmEvent
   * @description Deletes the current alarm event
   * @returns {void}
   */
  deleteAlarmEvent () {
    if (confirm('Alarm wirklich löschen?')) {
      this.manager.deleteAlarmEvent(this.currentAlarmSetId, this.currentAlarmEventId)
      this.openAlarmSetEditor(this.currentAlarmSetId)
    }
  }

  /**
   * @function duplicateAlarmEvent
   * @description Duplicates the current alarm event
   * @returns {void}
   */
  duplicateAlarmEvent () {
    const result = this.manager.duplicateAlarmEvent(this.currentAlarmSetId, this.currentAlarmEventId)
    if (result) {
      this.openAlarmSetEditor(this.currentAlarmSetId)
    }
  }

  /**
   * @function testAlarm
   * @description Saves current form input to temporary object and plays the alarm for testing without leaving screen
   * @returns {void}
   */
  async testAlarm () {
    const alarmSet = this.manager.alarmSets.find(set => set.id === this.currentAlarmSetId)
    if (!alarmSet) {
      return
    }

    const event = alarmSet.alarmEvents.find(e => e.id === this.currentAlarmEventId)
    if (!event) {
      return
    }

    // Temporarily update event object with current form values (without saving or navigating)
    const originalEvent = {
      time: event.time,
      timePlayback: event.timePlayback,
      message: event.message,
      gong: event.gong
    }

    event.time = document.getElementById('alarm-event-time').value || '00:00'
    event.timePlayback = document.getElementById('alarm-event-timeplayback').checked
    event.message = document.getElementById('alarm-event-message').value

    const gongSelect = document.getElementById('alarm-event-gong').value
    if (gongSelect === 'custom' && this.customGongFile) {
      event.gong = this.customGongFile
    } else {
      event.gong = gongSelect
    }

    // Play alarm with updated values
    await this.manager.playAlarm(event, alarmSet.audioVolume)

    // Restore original values (undo temporary changes)
    event.time = originalEvent.time
    event.timePlayback = originalEvent.timePlayback
    event.message = originalEvent.message
    event.gong = originalEvent.gong
  }

  /**
   * @function saveAlarmSet
   * @description Saves the current alarm set
   * @returns {void}
   */
  saveAlarmSet () {
    const name = document.getElementById('alarm-set-name').value.trim()

    if (!name) {
      alert('Bitte geben Sie einen Namen ein')
      return
    }

    if (this.currentAlarmSetId) {
      // Update existing
      const alarmSet = this.manager.alarmSets.find(set => set.id === this.currentAlarmSetId)
      if (alarmSet) {
        alarmSet.name = name
        alarmSet.enabled = document.getElementById('alarm-set-enabled').checked
        alarmSet.audioVolume = parseInt(document.getElementById('alarm-set-volume').value)
        alarmSet.weekdays = this.getSelectedWeekdays()
        this.manager.saveToStorage()
      }
    } else {
      // Create new
      const newSet = this.manager.createAlarmSet(name)
      newSet.enabled = document.getElementById('alarm-set-enabled').checked
      newSet.audioVolume = parseInt(document.getElementById('alarm-set-volume').value)
      newSet.weekdays = this.getSelectedWeekdays()
      this.manager.saveToStorage()
      this.currentAlarmSetId = newSet.id
    }

    this.openConfiguration()
  }

  /**
   * @function deleteAlarmSet
   * @description Deletes the current alarm set
   * @returns {void}
   */
  deleteAlarmSet () {
    if (!this.currentAlarmSetId) {
      return
    }

    if (confirm('Weckergruppe wirklich löschen?')) {
      this.manager.deleteAlarmSet(this.currentAlarmSetId)
      this.openConfiguration()
    }
  }

  /**
   * @function duplicateAlarmSet
   * @description Duplicates the current alarm set
   * @returns {void}
   */
  duplicateAlarmSet () {
    if (!this.currentAlarmSetId) {
      return
    }

    const result = this.manager.duplicateAlarmSet(this.currentAlarmSetId)
    if (result) {
      this.openConfiguration()
    }
  }

  /**
   * @function editAlarmFromDashboard
   * @description Opens alarm event editor directly from dashboard
   * @param {number} alarmSetId - The ID of the parent alarm-set
   * @param {number} eventId - The ID of the event to edit
   * @returns {void}
   */
  editAlarmFromDashboard (alarmSetId, eventId) {
    this.currentAlarmSetId = alarmSetId
    this.editAlarmEvent(eventId)
  }

  /**
   * @function closePage
   * @description Closes the current page and returns to configuration
   * @returns {void}
   */
  closePage () {
    if (this.currentPage === 'alarm-event-editor') {
      this.openAlarmSetEditor(this.currentAlarmSetId)
    } else if (this.currentPage === 'alarm-set-editor') {
      this.openConfiguration()
    } else if (this.currentPage === 'config') {
      this.openDashboard()
    }
  }

  /**
   * @function updateDashboard
   * @description Updates the dashboard with upcoming alarms
   * @returns {void}
   */
  updateDashboard () {
    const upcoming = this.manager.getUpcomingAlarms()
    const container = document.getElementById('upcoming-alarms')

    if (!container) {
      return
    }

    if (upcoming.length === 0) {
      container.innerHTML = '<p class="no-alarms">Keine Wecker in den nächsten 24 Stunden</p>'
      return
    }

    const now = new Date()
    const currentMinutes = now.getHours() * 60 + now.getMinutes()

    container.innerHTML = upcoming.map(alarm => {
      const remainingMinutes = alarm.order - currentMinutes
      const remainingHours = Math.floor(remainingMinutes / 60)
      const remainingMins = remainingMinutes % 60
      const remainingText = remainingHours > 0
        ? `${remainingHours}:${String(remainingMins).padStart(2, '0')} h`
        : `${remainingMins} min`

      return `
      <ons-list-item>
        <div class="left">
          <ons-icon icon="md-schedule"></ons-icon>
        </div>
        <div class="center">
          <span class="alarm-set-name-label">${alarm.alarmSetName}</span>
          <span class="alarm-time">${alarm.alarmEvent.time}</span>
          <span class="alarm-message">${alarm.alarmEvent.message || '(keine Nachricht)'}</span>
        </div>
        <div class="right" style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
          <span style="font-size: 12px; color: #7f8c8d; white-space: nowrap;">in ${remainingText}</span>
          <ons-button modifier="quiet" onclick="app.editAlarmFromDashboard(${alarm.alarmSetId}, ${alarm.alarmEvent.id})">
            <ons-icon icon="md-edit"></ons-icon>
          </ons-button>
        </div>
      </ons-list-item>
    `
    }).join('')
  }

  /**
   * @function updateConfigScreen
   * @description Updates the configuration screen with alarm sets
   * @returns {void}
   */
  updateConfigScreen () {
    const container = document.getElementById('alarm-sets-list')

    if (this.manager.alarmSets.length === 0) {
      container.innerHTML = '<p class="no-alarms">Keine Weckergruppen vorhanden. Erstellen Sie eine neue!</p>'
      return
    }

    container.innerHTML = this.manager.alarmSets.map(alarmSet => {
      const eventCount = alarmSet.alarmEvents.length
      return `
        <div class="alarm-set-card">
          <div class="alarm-set-card-header">
            <div class="alarm-set-name">${alarmSet.name}</div>
            <div class="alarm-set-status ${!alarmSet.enabled ? 'disabled' : ''}">
              ${alarmSet.enabled ? '✓ Aktiv' : '⊘ Inaktiv'}
            </div>
          </div>
          <div class="alarm-set-events-preview">
            ${eventCount} Alarm${eventCount === 1 ? '' : 'e'} • Lautstärke: ${alarmSet.audioVolume}%
          </div>
          <div class="alarm-set-actions" style="margin-top: 12px;">
            <button class="alarm-set-action-btn icon-only" onclick="app.openAlarmSetEditor(${alarmSet.id})" title="Bearbeiten"><ons-icon icon="md-edit"></ons-icon></button>
            <button class="alarm-set-action-btn icon-only" onclick="app.duplicateAlarmSetQuick(${alarmSet.id})" title="Kopieren"><ons-icon icon="md-copy"></ons-icon></button>
            <button class="alarm-set-action-btn icon-only danger" onclick="app.deleteAlarmSetQuick(${alarmSet.id})" title="Löschen"><ons-icon icon="md-delete"></ons-icon></button>
          </div>
        </div>
      `
    }).join('')
  }

  /**
   * @function duplicateAlarmSetQuick
   * @description Quickly duplicates an alarm set from the config screen
   * @param {number} alarmSetId - The ID of the alarm set to duplicate
   * @returns {void}
   */
  duplicateAlarmSetQuick (alarmSetId) {
    this.manager.duplicateAlarmSet(alarmSetId)
    this.updateConfigScreen()
  }

  /**
   * @function duplicateAlarmEventQuick
   * @description Quickly duplicates an alarm event
   * @param {number} eventId - The ID of the event to duplicate
   * @returns {void}
   */
  duplicateAlarmEventQuick (eventId) {
    this.manager.duplicateAlarmEvent(this.currentAlarmSetId, eventId)
    this.loadAlarmSetEditor(this.currentAlarmSetId)
  }
}

// Initialize app when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    window.app = new AppController()
  })
} else {
  window.app = new AppController()
}

// Keep service worker active to handle alarms
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.ready.then(() => {
    console.warn('Service Worker is ready')
  })
}
