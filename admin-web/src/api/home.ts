import { http } from '@/utils/request'
import type { BannerItem, CityItem } from '@/types'

export interface BannerSaveParams {
  id?: number
  title?: string
  subtitle?: string
  emoji?: string
  imageUrl?: string
  linkUrl?: string
  sortOrder?: number
  status?: number
}

export interface CitySaveParams {
  id?: number
  name: string
  cover?: string
  rating?: number
  sortOrder?: number
  status?: number
}

// ── Banner ──

export function getBannerList() {
  return http.get<BannerItem[]>('/admin/home/banner/list')
}

export function addBanner(data: BannerSaveParams) {
  return http.post<void>('/admin/home/banner', data)
}

export function updateBanner(data: BannerSaveParams) {
  return http.put<void>('/admin/home/banner', data)
}

export function deleteBanner(id: number) {
  return http.delete<void>(`/admin/home/banner/${id}`)
}

export function updateBannerStatus(id: number, status: number) {
  return http.put<void>(`/admin/home/banner/${id}/status/${status}`)
}

// ── 城市 ──

export function getCityList() {
  return http.get<CityItem[]>('/admin/home/city/list')
}

export function addCity(data: CitySaveParams) {
  return http.post<void>('/admin/home/city', data)
}

export function updateCity(data: CitySaveParams) {
  return http.put<void>('/admin/home/city', data)
}

export function deleteCity(id: number) {
  return http.delete<void>(`/admin/home/city/${id}`)
}

export function updateCityStatus(id: number, status: number) {
  return http.put<void>(`/admin/home/city/${id}/status/${status}`)
}
