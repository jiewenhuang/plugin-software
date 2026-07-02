import { definePlugin } from '@halo-dev/ui-shared'
import { IconComputer } from '@halo-dev/components'
import { markRaw } from 'vue'
import { RouterView } from 'vue-router'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/software',
        name: 'SoftwareRoot',
        component: RouterView,
        redirect: { name: 'SoftwareList' },
        children: [
          {
            path: 'apps',
            name: 'SoftwareList',
            component: () => import('@/views/SoftwareList.vue'),
            meta: {
              title: '软件列表',
              searchable: true,
              permissions: ['plugin:software:view'],
              menu: {
                name: '软件列表',
                priority: 0,
              },
            },
          },
          {
            path: 'editor',
            name: 'SoftwareEditor',
            component: () => import('@/views/SoftwareEditor.vue'),
            meta: {
              title: '软件编辑',
              searchable: false,
              permissions: ['plugin:software:manage'],
            },
          },
          {
            path: 'resources',
            name: 'SoftwareResourcesLegacy',
            redirect: (to) => {
              const type = String(to.query.type || 'categories')
              const routeMap: Record<string, string> = {
                categories: '/software/categories',
                tags: '/software/tags',
                topics: '/software/topics',
                downloads: '/software/download-assets',
              }
              return {
                path: routeMap[type] || '/software/categories',
                query: {},
              }
            },
          },
          {
            path: 'categories',
            name: 'SoftwareCategories',
            component: () => import('@/views/SoftwareResourceManager.vue'),
            meta: {
              title: '分类管理',
              searchable: false,
              permissions: ['plugin:software:manage'],
              menu: {
                name: '分类管理',
                priority: 10,
              },
            },
          },
          {
            path: 'tags',
            name: 'SoftwareTags',
            component: () => import('@/views/SoftwareResourceManager.vue'),
            meta: {
              title: '标签管理',
              searchable: false,
              permissions: ['plugin:software:manage'],
              menu: {
                name: '标签管理',
                priority: 20,
              },
            },
          },
          {
            path: 'topics',
            name: 'SoftwareTopics',
            component: () => import('@/views/SoftwareResourceManager.vue'),
            meta: {
              title: '专题管理',
              searchable: false,
              permissions: ['plugin:software:manage'],
              menu: {
                name: '专题管理',
                priority: 30,
              },
            },
          },
          {
            path: 'download-assets',
            name: 'SoftwareDownloadAssets',
            component: () => import('@/views/SoftwareResourceManager.vue'),
            meta: {
              title: '下载源管理',
              searchable: false,
              permissions: ['plugin:software:manage'],
              menu: {
                name: '下载源管理',
                priority: 40,
              },
            },
          },
        ],
        meta: {
          title: '软件',
          searchable: true,
          permissions: ['plugin:software:view'],
          menu: {
            name: '软件',
            group: 'content',
            icon: markRaw(IconComputer),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
