<template>
  <div class="app-shell">
    <header class="app-header">
      <h1>Instakill</h1>
      <nav v-if="user">
        <span class="user">@{{ user.username }}</span>
        <button @click="toggleAnalytics">{{ view === 'analytics' ? 'Feed' : 'Analytics' }}</button>
        <button @click="logout">Logout</button>
      </nav>
    </header>

    <main class="content">
      <section v-if="!user" class="card">
        <div class="tabs">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <form @submit.prevent="submitAuth" class="form">
          <input v-model="authForm.usernameOrEmail" v-if="authMode === 'login'" placeholder="Username or email" required>
          <input v-model="authForm.username" v-else placeholder="Username" required minlength="3">
          <input v-model="authForm.email" v-if="authMode === 'register'" type="email" placeholder="Email" required>
          <input v-model="authForm.password" type="password" placeholder="Password" required minlength="6">
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit">Continue</button>
        </form>
      </section>

      <section v-else-if="view === 'analytics'" class="analytics">
        <p v-if="!analytics">Loading analytics...</p>
        <template v-else>
          <div class="card">
            <h2>Rank by average engagement</h2>
            <p v-if="!analytics.engagementRank">You have no posts yet.</p>
            <p v-else>
              <strong>Top {{ analytics.engagementRank.topPercent.toFixed(2) }}%</strong>
              of users (#{{ analytics.engagementRank.rank }} / {{ analytics.engagementRank.totalUsers }}),
              average engagement {{ analytics.engagementRank.averageEngagement.toFixed(2) }} per post
            </p>
          </div>

          <div class="card">
            <h2>Like-to-comment ratio</h2>
            <p>
              <strong>{{ analytics.likeCommentRatio.ratio != null ? analytics.likeCommentRatio.ratio.toFixed(2) : '—' }}</strong>
              likes per comment ({{ analytics.likeCommentRatio.likes }} likes, {{ analytics.likeCommentRatio.comments }} comments)
            </p>
          </div>

          <div class="card">
            <h2>Per week</h2>
            <table class="stats-table">
              <thead><tr><th>Week of</th><th>Posts</th><th>Likes</th><th>Comments</th></tr></thead>
              <tbody>
                <tr v-for="w in analytics.weekly" :key="w.weekStart">
                  <td>{{ w.weekStart }}</td><td>{{ w.posts }}</td><td>{{ w.likes }}</td><td>{{ w.comments }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="card">
            <h2>Top 5 posts by engagement</h2>
            <table class="stats-table">
              <thead><tr><th>Post</th><th>Likes</th><th>Comments</th><th>Engagement</th></tr></thead>
              <tbody>
                <tr v-for="p in analytics.topPosts" :key="p.id">
                  <td>{{ p.content.length > 60 ? p.content.slice(0, 57) + '...' : p.content }}</td>
                  <td>{{ p.likes }}</td><td>{{ p.comments }}</td><td>{{ p.engagement }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="card">
            <h2>Average likes per post by media type</h2>
            <table class="stats-table">
              <thead><tr><th>Media type</th><th>Posts</th><th>Avg likes</th></tr></thead>
              <tbody>
                <tr v-for="m in analytics.byMediaType" :key="m.mediaType">
                  <td>{{ m.mediaType }}</td><td>{{ m.posts }}</td><td>{{ m.averageLikes.toFixed(2) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>

      <section v-else class="feed">
        <form @submit.prevent="createNewPost" class="card form composer">
          <textarea v-model="composer.content" rows="3" placeholder="Share something..." required></textarea>
          <input ref="fileInput" type="file" @change="incarcaFisier" accept="image/*,video/mp4,video/quicktime,video/webm" />
          <input v-model="composer.imageUrl" type="text" placeholder="...sau lipește un URL de imagine/video" />
          <small v-if="uploadStatus">{{ uploadStatus }}</small>
          <div class="actions">
            <button type="submit" :disabled="uploading">Post</button>
          </div>
        </form>

        <div class="feed-toolbar">
          <h2>Feed</h2>
          <div class="sort">
            <button :class="{ active: sort === 'new' }" @click="changeSort('new')">Newest</button>
            <button :class="{ active: sort === 'trending' }" @click="changeSort('trending')">Trending</button>
          </div>
        </div>

        <article v-for="post in posts" :key="post.id" class="card post">
          <header>
            <div>
              <h3>@{{ post.author.username }}</h3>
              <small>{{ formatDate(post.createdAt) }}</small>
            </div>
            <div class="metrics">
              <span>{{ post.likeCount }} likes</span>
              <span> | </span>
              <span>{{ post.commentCount }} comments</span>
            </div>
          </header>
          <p>{{ post.content }}</p>
          <video v-if="isVideo(post.imageUrl)" :src="post.imageUrl" controls style="max-width: 100%; border-radius: 8px; margin-top: 10px;"></video>
          <img v-else-if="post.imageUrl" :src="post.imageUrl" alt="post image" style="max-width: 100%; border-radius: 8px; margin-top: 10px;">
          
          <!-- Comments Section -->
          <section class="comments">
            <div v-if="comments[post.id] && comments[post.id].length">
              <div v-for="comment in comments[post.id]" :key="comment.id" class="comment">
                <b>@{{ comment.author.username }}</b> <small>{{ formatDate(comment.createdAt) }}</small>
                <div>{{ comment.content }}</div>
              </div>
            </div>
            <div v-else class="no-comments">
              <em>No comments yet.</em>
            </div>
            <form v-if="user" @submit.prevent="submitComment(post.id)" class="comment-form">
              <input
                v-model="newComment[post.id]"
                :placeholder="'Add a comment...'"
                required
                minlength="1"
              />
              <button type="submit" :disabled="commentLoading[post.id]">Comment</button>
            </form>
            <div v-if="commentError[post.id]" class="error">{{ commentError[post.id] }}</div>
          </section>

          <footer>
            <button @click="togglePostLike(post)">
              {{ post.likedByCurrentUser ? 'Unlike' : 'Like' }}
            </button>
          </footer>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createPost, fetchMe, listFeed, login, register as registerUser, toggleLike, listComments, addComment, uploadMedia, fetchMyAnalytics } from './api'

const user = ref(null)
const posts = ref([])
const sort = ref('new')
const authMode = ref('login')
const error = ref('')
const authForm = reactive({ username: '', email: '', password: '', usernameOrEmail: '' })
const composer = reactive({ content: '', imageUrl: '' })
const uploading = ref(false)
const uploadStatus = ref('')
const fileInput = ref(null)
const view = ref('feed')
const analytics = ref(null)

// Comments state
const comments = reactive({})
const newComment = reactive({})
const commentLoading = reactive({})
const commentError = reactive({})

onMounted(async () => {
  await bootstrap()
})

async function bootstrap() {
  try {
    user.value = await fetchMe()
    await loadFeed()
  } catch (e) {
    user.value = null
  }
}

async function submitAuth() {
  error.value = ''
  try {
    if (authMode.value === 'login') {
      const result = await login({
        usernameOrEmail: authForm.usernameOrEmail,
        password: authForm.password
      })
      user.value = result.user
    } else {
      const result = await registerUser({
        username: authForm.username,
        email: authForm.email,
        password: authForm.password
      })
      user.value = result.user
    }
    await loadFeed()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Authentication failed'
  }
}

async function loadFeed() {
  posts.value = await listFeed({ sort: sort.value })
  // Load comments for each post
  for (const post of posts.value) {
    await loadComments(post.id)
  }
}

async function loadComments(postId) {
  try {
    commentError[postId] = ''
    comments[postId] = await listComments(postId)
  } catch (e) {
    comments[postId] = []
    commentError[postId] = 'Failed to load comments'
  }
}

async function submitComment(postId) {
  if (!newComment[postId] || !newComment[postId].trim()) return
  commentLoading[postId] = true
  commentError[postId] = ''
  try {
    await addComment(postId, { content: newComment[postId] })
    newComment[postId] = ''
    await loadComments(postId)
    // Optionally, update commentCount in post
    const post = posts.value.find(p => p.id === postId)
    if (post) post.commentCount = (comments[postId]?.length || 0)
  } catch (e) {
    commentError[postId] = e.response?.data?.detail || 'Failed to add comment'
  } finally {
    commentLoading[postId] = false
  }
}

async function createNewPost() {
  if (!composer.content.trim() || uploading.value) return
  await createPost({ content: composer.content, imageUrl: composer.imageUrl.trim() || null })
  composer.content = ''
  composer.imageUrl = ''
  uploadStatus.value = ''
  if (fileInput.value) fileInput.value.value = ''
  await loadFeed()
}

async function incarcaFisier(event) {
  const file = event.target.files[0]
  if (!file) return

  uploading.value = true
  uploadStatus.value = `Se încarcă ${file.name}...`
  try {
    const result = await uploadMedia(file)
    composer.imageUrl = result.mediaUrl
    uploadStatus.value = 'Fișier încărcat ✔'
  } catch (err) {
    composer.imageUrl = ''
    uploadStatus.value = err.response?.data?.detail || 'Upload eșuat'
  } finally {
    uploading.value = false
  }
}

async function toggleAnalytics() {
  if (view.value === 'analytics') {
    view.value = 'feed'
    return
  }
  view.value = 'analytics'
  analytics.value = null
  analytics.value = await fetchMyAnalytics()
}

function isVideo(url) {
  return /\.(mp4|mov|webm)(\?.*)?$/i.test(url || '')
}

async function togglePostLike(post) {
  const result = await toggleLike(post.id)
  post.likedByCurrentUser = result.liked
  post.likeCount = result.likeCount
}

function changeSort(next) {
  if (sort.value === next) return
  sort.value = next
  loadFeed()
}

function formatDate(iso) {
  return new Date(iso).toLocaleString()
}

async function logout() {
  try {
    await fetch('/auth/logout', { method: 'POST', credentials: 'include' })
  } catch (e) {
    // ignore
  }
  document.cookie = 'ACCESS_TOKEN=; Max-Age=0; path=/'
  user.value = null
  posts.value = []
}
</script>

<style scoped>
.app-shell {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.app-header nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.app-header button {
  background: #111827;
  color: #fff;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 999px;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.card {
  background: #fff;
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.1);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form input,
.form textarea {
  width: 100%;
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px solid #d1d5db;
}

.form button {
  align-self: flex-start;
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
}

.tabs {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.tabs button {
  background: transparent;
  border: none;
  font-weight: 600;
  color: #6b7280;
}

.tabs button.active {
  color: #111827;
}

.error {
  color: #ef4444;
}

.feed-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sort button {
  margin-left: 0.5rem;
  border: none;
  background: #e5e7eb;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
}

.sort button.active {
  background: #111827;
  color: #fff;
}

.post header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.post img {
  margin-top: 0.75rem;
  width: 100%;
  border-radius: 12px;
}

.post footer {
  margin-top: 1rem;
}

.post footer button {
  background: #111827;
  border: none;
  color: #fff;
  padding: 0.4rem 1.2rem;
  border-radius: 999px;
}
.comments {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}
.comment {
  margin-bottom: 0.7rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f3f4f6;
}
.no-comments {
  color: #6b7280;
  margin-bottom: 0.7rem;
}
.comment-form {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.comment-form input {
  flex: 1;
  padding: 0.5rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
}
.comment-form button {
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.4rem 1.1rem;
  border-radius: 999px;
}
</style>
