import type { NavigatorScreenParams } from '@react-navigation/native';

import type {
  AnalyzeResponse,
  BeautyAnalyzeResponse,
  BeautyItem,
  BeautyProductCandidate,
  Outfit,
  WardrobeItem,
} from '../api/types';

export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
  ForgotPassword: { email?: string } | undefined;
};

export type HomeStackParamList = {
  Home: undefined;
  ShopAssistant: undefined;
};

export type WardrobeStackParamList = {
  WardrobeList: undefined;
  AddItem: undefined;
  ConfirmItem: { analysis: AnalyzeResponse; imageUri: string };
  ItemDetail: { item: WardrobeItem };
};

export type BeautyStackParamList = {
  BeautyList: undefined;
  AddBeauty: undefined;
  BeautySearch: undefined;
  ConfirmBeauty: {
    analysis?: BeautyAnalyzeResponse;
    candidate?: BeautyProductCandidate;
    imageUri?: string;
  };
  BeautyDetail: { item: BeautyItem };
  EditBeauty: { item: BeautyItem };
};

export type AppTabParamList = {
  HomeTab: NavigatorScreenParams<HomeStackParamList>;
  WardrobeTab: NavigatorScreenParams<WardrobeStackParamList>;
  GetReadyTab: undefined;
  BeautyTab: NavigatorScreenParams<BeautyStackParamList>;
  ProfileTab: undefined;
};

export type AppStackParamList = {
  Tabs: NavigatorScreenParams<AppTabParamList>;
  VerifyEmail: undefined;
  Onboarding: undefined;
  StylePreferences: { onboarding?: boolean } | undefined;
  StyleProfile: undefined;
  SavedLooks: undefined;
  LookDetail: { look: Outfit };
  Favorites: undefined;
};
